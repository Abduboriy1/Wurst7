package net.wurstclient.hacks.minebox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.function.Predicate;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.util.math.BlockPos;
import net.wurstclient.WurstClient;

public class FindMineBoxEntity {
    private final List<BlockPos> visitedTargets = new ArrayList<>();
    private final Map<BlockPos, Long> respawnTimers = new HashMap<>();
    private final List<MineBoxEntity> targets = new ArrayList<>();

    private static final MinecraftClient MC = WurstClient.MC;

    private BlockPos currentTargetPos = null;
    private Entity currentTarget = null;

    public static final Predicate<Entity> IS_ENTITY = e -> e instanceof InteractionEntity
		|| e instanceof TextDisplayEntity;

    public void scan() {
        Stream<Entity> stream = StreamSupport.stream(MC.world.getEntities().spliterator(), true)
        .filter(IS_ENTITY);
        targets.clear();

        for(Entity e : stream.map(e -> e).collect(Collectors.toList()))
        {	
            if(e instanceof TextDisplayEntity)
            {
                MineBoxEntity mineBoxEntity = new MineBoxEntity((TextDisplayEntity) e);
                if(mineBoxEntity.getMineBoxEntityType() == MineBoxEntity.MineBoxEntityType.MINER) {
                    targets.add(mineBoxEntity);
                }
            }
        }

        System.out.println("Found " + targets.size() + " mine box entities");
    }

    public void setCurrentTarget(Entity target) {
        this.currentTarget = target;
        this.currentTargetPos = target.getBlockPos();
    }

    @SuppressWarnings("unlikely-arg-type")
    public MineBoxEntity findClosestTarget() {
        return targets.stream()
                .filter(target -> !visitedTargets.contains(target) && hasRespawned(target))
                .min(Comparator.comparingDouble(target -> target.getPos().getSquaredDistance(MC.player.getPos())))
                .orElse(null);
    }

    public void markTargetVisited(BlockPos target) {
        visitedTargets.add(target);
        respawnTimers.put(target, System.currentTimeMillis());
    }

    public boolean hasRespawned(MineBoxEntity target) {
        if (!respawnTimers.containsKey(target.getPos())) {
            return true;
        }

        long elapsed = System.currentTimeMillis() - respawnTimers.get(target.getPos());
        return elapsed >= target.getRespawnTime();
    }

    public boolean isAtTarget() {
        return MC.player.getBlockPos().equals(currentTargetPos);
    }
}