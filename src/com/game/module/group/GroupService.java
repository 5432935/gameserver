package com.game.module.group;

import com.game.data.GroupCopyCfg;
import com.game.data.MonsterConfig;
import com.game.data.Response;
import com.game.module.copy.CopyExtension;
import com.game.module.copy.CopyInstance;
import com.game.module.copy.CopyService;
import com.game.module.goods.GoodsEntry;
import com.game.module.goods.GoodsService;
import com.game.module.log.LogConsume;
import com.game.module.player.Player;
import com.game.module.player.PlayerData;
import com.game.module.player.PlayerService;
import com.game.module.scene.SceneService;
import com.game.module.task.Task;
import com.game.module.task.TaskService;
import com.game.params.IProtocol;
import com.game.params.Int2Param;
import com.game.params.IntParam;
import com.game.params.ListParam;
import com.game.params.copy.CopyResult;
import com.game.params.group.GroupStageVO;
import com.game.params.group.GroupVO;
import com.game.params.group.SelfGroupCopyVO;
import com.game.params.scene.SMonsterVo;
import com.game.params.scene.SkillHurtVO;
import com.game.params.worldboss.MonsterHurtVO;
import com.game.util.ConfigData;
import com.game.util.JsonUtils;
import com.game.util.TimeUtil;
import com.google.common.collect.Maps;
import com.server.SessionManager;
import com.server.util.ServerLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by lucky on 2017/9/4.
 */
@Service
public class GroupService {

    //团队信息推送协议号
    private final static int CMD_GROUP_INFO = 5100;
    //解散推送
    private final static int CMD_DISMISS = 5101;
    //成员准备
    private final static int CMD_MEMBE_READY = 5102;
    //阶段奖励
    private final static int CMD_STAGE_AWARD = 5103;
    private final static int CMD_STAGE_ENTER = 5017;
    //开始游戏
    private final static int CMD_START_GAME = 5015;
    private final static int CMD_STAGE_INFO = 5018;
    //被踢
    private final static int CMD_KICK = 5104;

    private static final int CMD_MONSTER_INFO = 4910; //同步怪物相关信息

    //ID生成
    private final AtomicInteger IdGen = new AtomicInteger(100);
    /**
     * 团队列表
     */
    private final Map<Integer, Group> groupMap = new ConcurrentHashMap<>();

    @Autowired
    private PlayerService playerService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private CopyService copyService;
    @Autowired
    private SceneService sceneService;
    @Autowired
    private GoodsService goodsService;

    /**
     * 获取团队列表
     */
    public ListParam getGroupList() {
        ListParam<GroupVO> result = new ListParam();
        result.params = new ArrayList();
        for (Group group : groupMap.values()) {
            if (group.isOpenFlag() && !group.hasFightTeam() && !group.isStart()) {
                result.params.add(groupToProto(group));
            }
        }
        return result;
    }

    /**
     * 获取团队次数
     */
    public IntParam getGroupInfo(int playerId) {
        IntParam param = new IntParam();
        PlayerData data = playerService.getPlayerData(playerId);
        param.param = data.getGroupTimes();
        return param;
    }

    /**
     * 创建团队
     *
     * @param playerId
     * @param level
     */
    public IntParam createGroup(int playerId, int level, int groupCopyId) {
        IntParam param = new IntParam();
//        if (!checkOpen()) {
//            param.param = Response.TEAM_TIME_OVER;
//            return param;
//        }

        Player player = playerService.getPlayer(playerId);
        if (player.getGroupId() != 0) {
            param.param = Response.TEAM_TURE;
            return param;
        }

        if (player.getLev() < level) {
            param.param = Response.TEAM_NO_LEVEL_QI;
            return param;
        }
        PlayerData data = playerService.getPlayerData(playerId);
        if (data.getGroupTimes() < 1) {
            param.param = Response.TEAM_NO_NUM;
            return param;
        }
        int groupId = IdGen.getAndIncrement();
        GroupCopyCfg groupCopyCfg = ConfigData.getConfig(GroupCopyCfg.class, groupCopyId);
        Group group = new Group(groupId, playerId, level, groupCopyId, groupCopyCfg.stage1Copy[0]);
        int groupTeamId = group.addTeamMember(playerId, player.getHp(),
                player.getVocation(), player.getFight(), player.getLev(), player.getName());
        groupMap.put(groupId, group);
        player.setGroupId(groupId);
        player.setGroupTeamId(groupTeamId);
        switch (groupCopyCfg.stageCount) {
            case 3: {
                Map<Integer, GroupTask> map = new HashMap<>();
                for (int[] m : groupCopyCfg.stage3Mission) {
                    int v = 0;
                    if (m.length == 4) {
                        v = m[3];
                    }
                    map.put(m[1], new GroupTask(m[0], m[2], m[1], v));
                }
                group.addTask(3, map);
            }
            case 2: {
                Map<Integer, GroupTask> map = new HashMap<>();
                for (int[] m : groupCopyCfg.stage2Mission) {
                    int v = 0;
                    if (m.length == 4) {
                        v = m[3];
                    }
                    map.put(m[1], new GroupTask(m[0], m[2], m[1], v));
                }
                group.addTask(2, map);
            }
            case 1: {
                Map<Integer, GroupTask> map = new HashMap<>();
                for (int[] m : groupCopyCfg.stage1Mission) {
                    int v = 0;
                    if (m.length == 4) {
                        v = m[3];
                    }
                    map.put(m[1], new GroupTask(m[0], m[2], m[1], v));
                }
                group.addTask(1, map);
            }
        }

        ServerLogger.info("group size = " + groupMap.size());

        broadcastGroup(group);

        param.param = Response.SUCCESS;
        return param;
    }

    /**
     * 检测活动是否开启
     *
     * @return
     */
    public boolean checkOpen() {
        return TimeUtil.checkTimeIn(ConfigData.globalParam().groupCopyOpenTime);
    }

    /**
     * 解散团队
     *
     * @param playerId
     */
    public void dismissGroup(int playerId) {
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.remove(player.getGroupId());
        if (group == null) {
            String key = sceneService.getGroupKey(player);
            SessionManager.getInstance().removeFromGroup(key, player.getPlayerId());
            player.setGroupId(0);
            player.setGroupTeamId(0);
            ServerLogger.info("groupId = " + player.getGroupId());
            return;
        }

        if (group.getLeader() != playerId) {
            return;
        }
        broadcastGroupDismiss(group);
        group.clear();
    }

    /**
     * 团队信息广播
     * 解散
     *
     * @param group
     */
    private void broadcastGroupDismiss(Group group) {
        IntParam param = new IntParam();
        param.param = group.getId();
        Map<Integer, GroupTeam> teamMap = group.getTeamMap();
        for (GroupTeam team : teamMap.values()) {
            for (int playerId : team.getMembers().keySet()) {
                Player player = playerService.getPlayer(playerId);
                String key = sceneService.getGroupKey(player);
                SessionManager.getInstance().removeFromGroup(key, player.getPlayerId());
                player.setGroupId(0);
                player.setGroupTeamId(0);
                SessionManager.getInstance().sendMsg(CMD_DISMISS, param, playerId);
            }
        }
    }


    /**
     * 团长调整队伍
     *
     * @param playerId
     * @param toTeamId
     * @param targetId
     */
    public IntParam leaderChangeMember(int playerId, int toTeamId, int targetId) {
        IntParam param = new IntParam();
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {
            ServerLogger.info("groupId = " + player.getGroupId());
            param.param = Response.GROUP_NO_EXIT;
            return param;
        }

        if (group.getLeader() != playerId) {
            param.param = Response.GROUP_LIMIT;
            return param;
        }

        Player targetPlayer = playerService.getPlayer(targetId);
        if (targetPlayer.getGroupTeamId() == toTeamId) { //队伍没调整
            param.param = Response.ERR_PARAM;
            return param;
        }
        int ret = group.changeTeam(targetPlayer.getGroupTeamId(), toTeamId, targetId);
        if (ret == Response.SUCCESS) {//更新数据
            targetPlayer.setGroupTeamId(toTeamId);
            broadcastGroup(group);
        }
        param.param = ret;
        return param;
    }

    /**
     * 任命队长
     *
     * @param playerId
     * @param teamLeader
     */
    public IntParam changeTeamLeader(int playerId, int teamLeader) {
        IntParam param = new IntParam();
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {
            ServerLogger.info("groupId = " + player.getGroupId());
            param.param = Response.GROUP_NO_EXIT;
            return param;
        }

        if (group.getLeader() != playerId) {
            param.param = Response.GROUP_LIMIT;
            return param;
        }

        Player targetPlayer = playerService.getPlayer(teamLeader);
        int ret = group.changeTeamLeader(targetPlayer.getGroupTeamId(), teamLeader);
        if (ret == Response.SUCCESS) {
            broadcastGroup(group);
        }
        param.param = ret;
        return param;
    }

    private GroupVO groupToProto(Group group) {
        GroupVO vo = group.toProto();
        Player player = playerService.getPlayer(group.getLeader());
        vo.leaderVocation = player.getVocation();
        vo.leaderLevel = player.getLev();
        vo.groupName = player.getName();
        return vo;
    }

    /**
     * 团队信息广播
     * 成员加入，离开，信息变更?
     *
     * @param group
     */
    private void broadcastGroup(Group group) {
        broadcastGroup(group, CMD_GROUP_INFO, group.toProto());
    }

    private void broadcastGroup(Group group, int cmd, IProtocol vo) {
        Map<Integer, GroupTeam> teamMap = group.getTeamMap();
        for (GroupTeam team : teamMap.values()) {
            for (int playerId : team.getMembers().keySet()) {
                SessionManager.getInstance().sendMsg(cmd, vo, playerId);
            }
        }
    }

    /**
     * 团队信息广播,奖励
     * 成员加入，离开，信息变更?
     *
     * @param group
     */
    private void broadcastGroupAward(Group group, IProtocol awards, List<GoodsEntry> items) {
        Map<Integer, GroupTeam> teamMap = group.getTeamMap();
        for (GroupTeam team : teamMap.values()) {
            for (int playerId : team.getMembers().keySet()) {
                SessionManager.getInstance().sendMsg(CMD_STAGE_AWARD, awards, playerId);
                ServerLogger.info("group award ==>" + playerId);
                goodsService.addRewards(playerId, items, LogConsume.COPY_REWARD, group.groupCopyId);

                //扣除次数，只扣一次
                if (!team.checkCostTimes(playerId)) {
                    team.setCostTimes(playerId);
                    PlayerData data = playerService.getPlayerData(playerId);
                    data.setGroupTimes(data.getGroupTimes() - 1);


                }
            }
        }
    }

    /**
     * 队伍信息广播
     * 成员准备-取消准备
     *
     * @param groupTeam
     */
    private void broadcastGroupTeam(GroupTeam groupTeam, int cmd, IProtocol vo) {
        for (int playerId : groupTeam.getMembers().keySet()) {
            SessionManager.getInstance().sendMsg(cmd, vo, playerId);
        }
    }

    /**
     * 队伍信息广播
     * 成员准备-取消准备
     *
     * @param groupTeam
     */
    private void broadcastGroupTeam(GroupTeam groupTeam) {
        broadcastGroupTeam(groupTeam, CMD_MEMBE_READY, groupTeam.toProto());
    }

    /**
     * 邀请入团
     *
     * @param playerId
     * @param targetId
     */
    public IntParam invite(int playerId, int targetId) {
        IntParam param = new IntParam();
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        Player targetPlayer = playerService.getPlayer(targetId);

        if (targetPlayer.getGroupId() != 0) {
            ServerLogger.warn("groupId = " + player.getGroupId());
            param.param = Response.ERR_PARAM;
            return param;
        }

        if (group == null) {
            ServerLogger.warn("groupId = " + player.getGroupId());
            param.param = Response.GROUP_NO_EXIT;
            return param;
        }

        if (group.getLeader() != playerId) {
            param.param = Response.GROUP_LIMIT;
            return param;
        }

        if (group.isFull()) {
            param.param = Response.TEAM_FULL;
            return param;
        }


        int groupTeamId = group.addTeamMember(targetId, targetPlayer.getHp(),
                targetPlayer.getVocation(), targetPlayer.getFight(), targetPlayer.getLev(), targetPlayer.getName());
        targetPlayer.setGroupTeamId(groupTeamId);
        targetPlayer.setGroupId(player.getGroupId());

        broadcastGroup(group);
        param.param = Response.SUCCESS;
        return param;
    }


    public void inviteLinked(int playerId) {
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {
            return;
        }

        //messageService.sendSysMsg()

    }

    /**
     * 团队设置
     *
     * @param playerId
     * @param bOpen
     */
    public IntParam groupSet(int playerId, boolean bOpen) {
        IntParam param = new IntParam();
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {
            ServerLogger.warn("groupId = " + player.getGroupId());
            param.param = Response.GROUP_NO_EXIT;
            return param;
        }

        if (group.getLeader() != playerId) {
            param.param = Response.GROUP_LIMIT;
            return param;
        }
        group.setOpenFlag(bOpen);
        broadcastGroup(group);

        param.param = Response.SUCCESS;
        return param;
    }


    /**
     * 成员退出
     *
     * @param playerId
     */
    public IntParam memberExit(int playerId) {
        IntParam param = new IntParam();
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {
            ServerLogger.warn("groupId = " + player.getGroupId());
            param.param = Response.GROUP_NO_EXIT;
            return param;
        }

        if (player.getGroupTeamId() <= 0 || player.getGroupTeamId() > 6) {
            ServerLogger.warn("groupId = " + player.getGroupId());
            ServerLogger.warn(JsonUtils.object2String(group));
        }

        if (group.size() < 1) { //to protect
            groupMap.remove(group.getId());
            param.param = Response.ERR_PARAM;
            return param;
        }

        sceneService.exitScene(player);

        if (group.size() == 1) { //如果本来只有一个，解散掉
            group.clear();
            groupMap.remove(group.getId());

            String key = sceneService.getGroupKey(player);
            SessionManager.getInstance().removeFromGroup(key, player.getPlayerId());
            player.setGroupId(0);
            player.setGroupTeamId(0);
            param.param = Response.SUCCESS;
            return param;
        }

        group.memberExit(player.getGroupTeamId(), playerId);

        String key = sceneService.getGroupKey(player);
        SessionManager.getInstance().removeFromGroup(key, player.getPlayerId());
        player.setGroupId(0);
        player.setGroupTeamId(0);
        broadcastGroup(group);

        param.param = Response.SUCCESS;
        return param;
    }

    /**
     * 加入团队
     *
     * @param playerId
     * @param groupId
     */
    public IntParam joinGroup(int playerId, int groupId) {
        IntParam param = new IntParam();
        PlayerData data = playerService.getPlayerData(playerId);
        if (data.getGroupTimes() < 1) {
            param.param = Response.TEAM_NO_NUM;
            return param;
        }
        Player player = playerService.getPlayer(playerId);
        if (player.getGroupId() == groupId) {
            param.param = Response.TEAM_TURE;
            return param;
        }
        Group group = groupMap.get(groupId);
        if (group == null) {
            ServerLogger.warn("groupId = " + player.getGroupId());
            param.param = Response.GROUP_NO_EXIT;
            return param;
        }

        if (player.getLev() < group.getLevel()) {
            param.param = Response.NO_LEV;
            return param;
        }

        //团队已经开始不能加入
        if(group.isStart())
        {
            param.param = Response.GROUP_ALREADY_START;
            return param;
        }

//        if (!group.isOpenFlag()) {
//            param.param = Response.TEAM_NO_OPEN;
//            return param;
//        }

        int groupTeamId = group.addTeamMember(playerId, player.getHp(),
                player.getVocation(), player.getFight(), player.getLev(), player.getName());
        if (groupTeamId == 0) {
            param.param = Response.TEAM_PEOPLE_REACHED;
            return param;
        }

        player.setGroupTeamId(groupTeamId);
        player.setGroupId(groupId);

        broadcastGroup(group);

        param.param = Response.SUCCESS;
        return param;
    }

    /**
     * 踢出成员
     *
     * @param playerId
     * @param targetId
     */
    public IntParam kickMember(int playerId, int targetId) {
        IntParam param = new IntParam();
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {
            ServerLogger.warn("groupId = " + player.getGroupId());
            param.param = Response.GROUP_NO_EXIT;
            return param;
        }

        if (group.getLeader() != playerId) {
            param.param = Response.GROUP_LIMIT;
            return param;
        }

        Player targetPlayer = playerService.getPlayer(targetId);
        sceneService.exitScene(targetPlayer);
        group.memberExit(targetPlayer.getGroupTeamId(), targetId);

        String key = sceneService.getGroupKey(player);
        SessionManager.getInstance().removeFromGroup(key, targetPlayer.getPlayerId());
        targetPlayer.setGroupTeamId(0);
        targetPlayer.setGroupId(0);

        broadcastGroup(group);

        param.param = Response.SUCCESS;
        SessionManager.getInstance().sendMsg(CMD_KICK, param, targetId);
        return param;
    }

    /**
     * 自己调整队伍
     *
     * @param playerId
     * @param toTeamId
     */
    public IntParam selfChangeTeam(int playerId, int toTeamId) {
        IntParam param = new IntParam();
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {
            ServerLogger.warn("groupId = " + player.getGroupId());
            param.param = Response.GROUP_NO_EXIT;
            return param;
        }

        if (toTeamId <= 0 || toTeamId > 6) {
            param.param = Response.ERR_PARAM;
            return param;
        }
        int ret = group.changeTeam(player.getGroupTeamId(), toTeamId, playerId);
        if (ret == Response.SUCCESS) {//更新数据
            player.setGroupTeamId(toTeamId);
            broadcastGroup(group);
        }
        param.param = ret;
        return param;
    }

    /**
     * 成员准备 or 取消准备
     *
     * @param playerId
     * @param bReady
     */
    public IntParam memberReady(int playerId, boolean bReady) {
        IntParam param = new IntParam();
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {
            ServerLogger.warn("groupId = " + player.getGroupId());
            param.param = Response.GROUP_NO_EXIT;
            return param;
        }
        GroupTeam groupTeam = group.getGroupTeam(player.getGroupTeamId());
        groupTeam.memberReady(playerId, bReady);
        broadcastGroupTeam(groupTeam);

        param.param = Response.SUCCESS;
        return param;
    }

    /**
     * 获取相关信息
     *
     * @param playerId
     * @return
     */
    public SelfGroupCopyVO getSelfGroupInfo(int playerId) {
        SelfGroupCopyVO vo = new SelfGroupCopyVO();
        PlayerData data = playerService.getPlayerData(playerId);
        vo.times = data.getGroupTimes();
        return vo;
    }

    /**
     * 开始挑战
     *
     * @param playerId
     * @return
     */
    public Int2Param startChallenge(int playerId, int copyId) {
        Int2Param param = new Int2Param();
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {
            ServerLogger.info("groupId = " + player.getGroupId());
            param.param1 = Response.GROUP_NO_EXIT;
            return param;
        }

        //如果团队还没开始，则只能是团长才能开战
        if(!group.isStart() && group.getLeader() != playerId)
        {
            param.param1 = Response.GROUP_LEADER_HAS_NOT_FIGHT;
            return param;
        }

        GroupTeam team = group.getGroupTeam(player.getGroupTeamId());
        if (team.isbFight()) {
            param.param1 = Response.TEAM_FIGHT;
            return param;
        }

        if (team.getLeader() != playerId) {
            param.param1 = Response.TEME_RBAC;
            return param;
        }

        if (!team.isReady()) { //是否准备
            param.param1 = Response.TEAM_NO_OK;
            return param;
        }

        team.setbFight(true);
        group.addCopyState(team.getId(), copyId);
        group.setStart(true);

        param.param1 = Response.SUCCESS;
        param.param2 = copyId;
        broadcastGroupTeam(team, CMD_START_GAME, param);
        broadcastGroup(group, CMD_STAGE_INFO, group.toStageCopyProto());
        broadcastGroup(group);

        for (int id : team.getMembers().keySet()) {
            taskService.doTask(id, Task.TYPE_PASS_TYPE_COPY, CopyInstance.TYPE_GROUP, 1);
        }

        //TODO 判断 当前副本已经通关，无法进入该副本。

        //TODO 判断 当前副本所属的阶段未开启，无法进入该副本。

        //TODO 判断 需要通过当前阶段，才可进入下一阶段。
        return null;
    }

    // 玩家技能处理
    public void handleSkillHurt(Player player, SkillHurtVO hurtVO) {
        int sceneId = player.getSceneId();
        CopyInstance copy = copyService.getCopyInstance(player.getCopyId());
        if (player.getCopyId() == 0) {
            return;
        }
        Map<Integer, SMonsterVo> monsters = copy.getMonsters(sceneId);

        Group group = groupMap.get(player.getGroupId());
        if (group == null) { //难道是解散队伍了?
            return;
        }
        GroupTeam team = group.getGroupTeam(player.getGroupTeamId());
        if (hurtVO.targetType == 0) {
            team.decHp(hurtVO.targetId, hurtVO.hurtValue);
            if (team.checkDeath()) {
                //副本失败
                sceneService.brocastToSceneCurLine(player, CopyExtension.COPY_FAIL, null);
                group.removeCopyState(team.getId());
//                team.setbFight(false);
//                team.clearReady();
                broadcastGroup(group, CMD_STAGE_INFO, group.toStageCopyProto());
                broadcastGroup(group);
            }
        } else {
            if (!player.checkHurt(hurtVO.hurtValue)) {
                SessionManager.getInstance().kick(player.getPlayerId());
                ServerLogger.warn("==================== 作弊玩家 Id = " + player.getPlayerId());
                return;
            }
            SMonsterVo monster = monsters.get(hurtVO.targetId);
            try {
                group.getLock().lock();
                if (monster.curHp <= 0) {
                    return;
                }
                monster.curHp -= hurtVO.hurtValue;
            } finally {
                group.getLock().unlock();
            }

            int hp = monster.curHp > 0 ? monster.curHp : 0;
            MonsterHurtVO ret = new MonsterHurtVO();
            ret.actorId = hurtVO.targetId;
            ret.curHp = hp;
            ret.hurt = hurtVO.hurtValue;
            ret.isCrit = hurtVO.isCrit;
            ret.type = 1;
            broadcastGroupTeam(team, CMD_MONSTER_INFO, ret);

            if (monster.curHp <= 0) {
                MonsterConfig monsterConfig = ConfigData.getConfig(MonsterConfig.class, monster.monsterId);
                Map<Integer, int[]> condParams = Maps.newHashMap();
                condParams.put(Task.FINISH_KILL, new int[]{monsterConfig.type, monster.monsterId, 1});
                condParams.put(Task.TYPE_KILL, new int[]{monsterConfig.type, 1});
                condParams.put(Task.TYPE_KILL, new int[]{0, 1});
                taskService.doTask(player.getPlayerId(), condParams);
                monsters.remove(hurtVO.targetId);
                if (copy.isOver()) { //战斗结束
                    group.removeCopyState(team.getId());
                    group.addPassCopy(copy.getCopyId());

                    //统一在onExitBattle设置，因为提早设置，会导致团长可以调整队伍，导致队伍的fightSize有问题。
//                    team.setbFight(false);
//                    team.clearReady();
                    //副本胜利
                    CopyResult result = new CopyResult();
                    GroupCopyCfg cfg = ConfigData.getConfig(GroupCopyCfg.class, group.groupCopyId);

                    group.completeTask(group.stage, GroupTaskType.PASS_COUNT, copy.getCopyId(), 0);
                    int pass = (int) ((System.currentTimeMillis() - copy.getCreateTime()) / 1000);
                    group.completeTask(group.stage, GroupTaskType.PASS_TIME, copy.getCopyId(), pass);
                    broadcastGroup(group, CMD_STAGE_INFO, group.toStageCopyProto());
                    // 更新次数,星级
                    for (int playerId : team.getMembers().keySet()) {
                        // 清除
                        copyService.getRewards(playerId, copy.getCopyId(), result);
                        copyService.removeCopy(playerId);
                        SessionManager.getInstance().sendMsg(CopyExtension.TAKE_COPY_REWARDS, result, playerId);
                    }
                    if (group.checkComplete(group.stage)) {
                        List<GoodsEntry> items = new ArrayList<>();

                        int[][] stageReward = null;
                        if (group.stage == 1) {
                            stageReward = cfg.stage1Reward;
                        }
                        if (group.stage == 2) {
                            stageReward = cfg.stage2Reward;
                        }
                        if (group.stage == 3) {
                            stageReward = cfg.stage3Reward;

                        }
                        if (stageReward == null) {
                            return;
                        }
                        int stagePass = (int) ((System.currentTimeMillis() - group.stageBeginTime) / 1000);
                        int[] arrRate = ConfigData.globalParam().groupRewardRate;
                        int rate = 0;
                        Int2Param awardParam = new Int2Param();
                        awardParam.param1 = group.stage;
                        awardParam.param2 = stagePass;
                        for (int i = 0; i < arrRate.length; i += 2) {
                            int time = arrRate[i];
                            int rateTmp = arrRate[i + 1];
                            if (stagePass <= time) {
                                rate = rateTmp;
                                break;
                            }
                        }
                        for (int[] arr : stageReward) {
                            items.add(new GoodsEntry(arr[0], arr[1]));
                        }
                        if (group.stage == 3) {
                            taskService.doTask(group.getLeader(), Task.TYPE_LEADER_PASS, copy.getCopyId());
                        }
                        if (group.stage <= 3) {
                            group.stageBeginTime = System.currentTimeMillis();
                            if (!group.awardStage.contains(group.stage)) {
                                group.awardStage.add(group.stage);
                                broadcastGroupAward(group, awardParam, items);
                            }
                            if (group.stage < 3) {
                                group.stage += 1;
                            }
                        }
                    }
                    broadcastGroup(group);
                }
            }
        }
    }

    /**
     * 请求进入
     *
     * @param playerId
     */
    public IntParam stageEnter(int playerId) {
        IntParam param = new IntParam();
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {
            ServerLogger.info("groupId = " + player.getGroupId());
            param.param = Response.GROUP_NO_EXIT;
            return param;
        }
        GroupTeam groupTeam = group.getGroupTeam(player.getGroupTeamId());
        if (groupTeam.getLeader() != playerId) {
            param.param = Response.TEME_RBAC;
            return param;
        }

        for (int id : groupTeam.getMembers().keySet()) {
            if (id != playerId) {
                SessionManager.getInstance().sendMsg(CMD_STAGE_ENTER, param, id);
            }
        }

        param.param = Response.SUCCESS;
        return param;
    }

    /**
     * 获取团队副本信息
     *
     * @param playerId
     * @return
     */
    public GroupStageVO getGroupStageInfo(int playerId) {
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {
            ServerLogger.info("groupId = " + player.getGroupId());
            return null;
        }
        GroupStageVO vo = group.toStageCopyProto();
        return vo;
    }

    /**
     * 进入战斗
     *
     * @param playerId
     */
    public void onExitBattle(int playerId) {
        try {
            Player player = playerService.getPlayer(playerId);
            Group group = groupMap.get(player.getGroupId());
            if (group == null) {//队伍解散
                return;
            }
            GroupTeam groupTeam = group.getGroupTeam(player.getGroupTeamId());
            if (groupTeam == null) {
                return;
            }
            int size = groupTeam.fightSize.decrementAndGet();
            if (size == 0) {
                groupTeam.setbFight(false);
                groupTeam.clearReady();
                group.removeCopyState(groupTeam.getId());
                broadcastGroup(group);
                broadcastGroup(group, CMD_STAGE_INFO, group.toStageCopyProto());
            }
            ServerLogger.info("Group Battle Hero Size = " + size);
        } catch (Exception e) {
            ServerLogger.err(e, "团队副本退出异常");
        }
    }

    /**
     * 进入战斗
     *
     * @param playerId
     * @return
     */
    public int onEnterBattle(int playerId, int copyId) {
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {
            return 0;
        }

        if (copyId == group.beginStageId && group.stageBeginTime == 0) {
            group.stageBeginTime = System.currentTimeMillis();
        }

        GroupTeam groupTeam = group.getGroupTeam(player.getGroupTeamId());
        int size = groupTeam.fightSize.incrementAndGet();
        ServerLogger.info("Group Battle Hero Size = " + size);
        return groupTeam.getLeader() * 100;
    }

    /**
     * 掉线，退出处理
     *
     * @param playerId
     */
    public void onLogout(int playerId) {
        try {
            Player player = playerService.getPlayer(playerId);
            if (player.getGroupId() != 0) {
                memberExit(playerId);
            }
        } catch (Exception e) {
            ServerLogger.err(e, "团队副本异常");
        }
    }

    public void chat(Player player, int cmd, IProtocol param) {
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {
            return;
        }
        broadcastGroup(group, cmd, param);
    }

    public void updateAttr(int playerId) {
        Player player = playerService.getPlayer(playerId);
        Group group = groupMap.get(player.getGroupId());
        if (group == null) {//队伍解散
            return;
        }
        GroupTeam groupTeam = group.getGroupTeam(player.getGroupTeamId());
        if (groupTeam == null) {
            return;
        }
        GroupTeamMember member = groupTeam.getMembers().get(playerId);
        member.setFight(player.getFight());
        member.setLev(player.getLev());
        broadcastGroup(group);
    }

    public Group getGroup(int groupId){
        return groupMap.get(groupId);
    }
}
