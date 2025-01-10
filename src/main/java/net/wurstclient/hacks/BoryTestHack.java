package net.wurstclient.hacks;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.ai.PathFinder;
import net.wurstclient.ai.PathProcessor;
import net.wurstclient.commands.PathCmd;
import net.wurstclient.events.MouseUpdateListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EspBoxSizeSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

@SearchTags({"bory test", "BoryTest", "bory tests"})
public final class BoryTestHack extends Hack implements UpdateListener, MouseUpdateListener, RenderListener {

    private MineBoxEntity currentTarget;
    private float nextYaw;
    private float nextPitch;

    private MineBoxEntityFinder entityFinder;
    private MineBoxPathFinder pathFinder;

    private final SliderSetting range = new SliderSetting("Range", 2, 1, 2, 0.05, SliderSetting.ValueDisplay.DECIMAL);
    private final SliderSetting fov = new SliderSetting("FOV", "Field of View", 360, 30, 360, 10, ValueDisplay.DEGREES);
    private final SliderSetting rotationSpeed = new SliderSetting("Rotation Speed", 600, 10, 3600, 10, ValueDisplay.DEGREES.withSuffix("/s"));
    private final CheckboxSetting damageIndicator = new CheckboxSetting("Damage Indicator", true);
    private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting("Box Size Mode");

    public BoryTestHack() {
        super("BoryTest");
        setCategory(Category.FUN);
        addSetting(range);
        addSetting(fov);
        addSetting(rotationSpeed);
        addSetting(damageIndicator);
        addSetting(boxSize);
    }

    @Override
    protected void onEnable() {
        entityFinder = new MineBoxEntityFinder();
        pathFinder = new MineBoxPathFinder();
        EVENTS.add(UpdateListener.class, this);
        EVENTS.add(MouseUpdateListener.class, this);
        EVENTS.add(RenderListener.class, this);
    }

    @Override
    protected void onDisable() {
        EVENTS.remove(UpdateListener.class, this);
        EVENTS.remove(MouseUpdateListener.class, this);
        EVENTS.remove(RenderListener.class, this);
        currentTarget = null;
        pathFinder = null;
        entityFinder = null;
        PathProcessor.releaseControls();
        System.out.println("BORY OFF");
    }

    @Override
    public void onUpdate() {
		
        if (currentTarget == null) {
            entityFinder.scan();
            currentTarget = entityFinder.findClosestTarget();
            return;
        }

		boolean isCurentTarget = currentTarget.matchesPlayerName();

		if (isCurentTarget) {
			
            Vec3d targetSpot = ParticleUtils.findReachableParticle(range.getValueSq());

            if (targetSpot != null) {
                currentTarget.setHitBox(targetSpot);
                faceAndAttack(targetSpot);
            }
        }

		if(!pathFinder.isDone()) {
			pathFinder.updatePathfinding(currentTarget.getPos());

			return;
		}

		if (pathFinder.isDone() && !isCurentTarget && currentTarget == entityFinder.findClosestTarget()) {
			IKeyBinding.get(MC.options.attackKey).simulatePress(true);
			IKeyBinding.get(MC.options.attackKey).simulatePress(false);

			return;
		}

		pathFinder.reSet();
		currentTarget = null;
    }

    @Override
    public void onMouseUpdate(MouseUpdateEvent event) {
        if (!pathFinder.isDone() || currentTarget == null) return;

        int diffYaw = (int) (nextYaw - MC.player.getYaw());
        int diffPitch = (int) (nextPitch - MC.player.getPitch());

        if (MathHelper.abs(diffYaw) < 1 && MathHelper.abs(diffPitch) < 1) return;

        event.setDeltaX(event.getDefaultDeltaX() + diffYaw);
        event.setDeltaY(event.getDefaultDeltaY() + diffPitch);
    }

    @Override
    public void onRender(MatrixStack matrixStack, float partialTicks) {
        if (pathFinder != null && pathFinder.hasPath()) {
            PathCmd pathCmd = WURST.getCmds().pathCmd;
            pathFinder.renderPath(matrixStack, pathCmd.isDebugMode(), pathCmd.isDepthTest());
        }
    }

    private void faceAndAttack(Vec3d targetSpot) {
		if(targetSpot == null) return;
        Rotation needed = RotationUtils.getNeededRotations(targetSpot);
        Rotation next = RotationUtils.slowlyTurnTowards(needed, rotationSpeed.getValueI() / 20F);

        nextYaw = next.yaw();
        nextPitch = next.pitch();

        if (RotationUtils.isAlreadyFacing(needed)) {
            IKeyBinding.get(MC.options.attackKey).simulatePress(true);
            IKeyBinding.get(MC.options.attackKey).simulatePress(false);
        }
    }

    // Utility class for particle handling
    public static class ParticleUtils {

        public static Vec3d findReachableParticle(double rangeSq) {
            ParticleManager particleManager = MinecraftClient.getInstance().particleManager;
            List<Vec3d> points = new ArrayList<>();

            try {
                Field particlesField = ParticleManager.class.getDeclaredField("particles");
                particlesField.setAccessible(true);

                @SuppressWarnings("unchecked")
                Map<ParticleTextureSheet, Queue<Particle>> particlesMap = (Map<ParticleTextureSheet, Queue<Particle>>) particlesField.get(particleManager);

                for (Queue<Particle> particleQueue : particlesMap.values()) {
                    for (Particle particle : particleQueue) {
                        if (isGlowParticle(particle) && isParticleReachable(particle, rangeSq)) {
                            points.add(particle.getBoundingBox().getCenter());
                        }
                    }
                }

                if (!points.isEmpty()) {
                    return findCenter(points);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }

            return null;
        }

        private static boolean isGlowParticle(Particle particle) {
            return particle.getClass().getSimpleName().contains("GlowParticle");
        }

        private static boolean isParticleReachable(Particle particle, double rangeSq) {
			Vec3d particlePos = particle.getBoundingBox().getCenter();
			BlockPos pos = new BlockPos((int) particlePos.x, (int) particlePos.y, (int) particlePos.z);
            BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
            return params != null && params.distanceSq() <= rangeSq;
        }

        private static Vec3d findCenter(List<Vec3d> points) {
            double sumX = 0, sumY = 0, sumZ = 0;
            for (Vec3d point : points) {
                sumX += point.x;
                sumY += point.y;
                sumZ += point.z;
            }
            return new Vec3d(sumX / points.size(), sumY / points.size(), sumZ / points.size());
        }
    }

	public class MineBoxEntity {

		private final BlockPos pos;
		private final boolean isHarvestable;
		private final MineBoxEntityType type;
		private final TextDisplayEntity entity;
		private Vec3d hitBox;
	
		public enum MineBoxEntityType {
			ALCHEMIST,
			LUMBERJACK,
			MINER,
			FISHERMAN,
			UNKNOWN
		}
	
		public MineBoxEntity(TextDisplayEntity entity) {
			this.entity = entity;
			this.pos = entity.getBlockPos();
			String text = entity.getText().getString();
			this.type = parseType(text);
	
			// Harvestable condition: text color is not black
			this.isHarvestable = entity.getText().getStyle().getColor() == null ||
					!"#000000".equals(entity.getText().getStyle().getColor().getHexCode());
		}
	
		private MineBoxEntityType parseType(String text) {
			if (text.contains("Alchemist")) {
				return MineBoxEntityType.ALCHEMIST;
			} else if (text.contains("Lumberjack")) {
				return MineBoxEntityType.LUMBERJACK;
			} else if (text.contains("Miner")) {
				return MineBoxEntityType.MINER;
			} else if (text.contains("Fisherman")) {
				return MineBoxEntityType.FISHERMAN;
			} else {
				return MineBoxEntityType.UNKNOWN;
			}
		}
	
		public BlockPos getPos() {
			return new BlockPos(pos.getX(), pos.getY() - 2, pos.getZ());
		}
	
		public boolean isHarvestable() {
			return isHarvestable;
		}
	
		public Vec3d getHitBox() {
			return hitBox;
		}
	
		public void setHitBox(Vec3d hitBox) {
			this.hitBox = hitBox;
		}
	
		public boolean matchesPlayerName() {
			return entity.getText().getString().contains(MC.player.getDisplayName().getString());
		}
	
		public MineBoxEntityType getType() {
			return type;
		}
	}
	
	class MineBoxEntityFinder {
	
		private final List<MineBoxEntity> targets = new ArrayList<>();
		private final MinecraftClient MC = MinecraftClient.getInstance();
	
		private static final Predicate<Entity> IS_VALID_ENTITY = e -> e instanceof TextDisplayEntity;
	
		public void scan() {
			Stream<Entity> stream = StreamSupport.stream(MC.world.getEntities().spliterator(), true)
					.filter(IS_VALID_ENTITY);
			
			targets.clear();
			for (Entity e : stream.collect(Collectors.toList())) {
				if (e instanceof TextDisplayEntity) {
					MineBoxEntity entity = new MineBoxEntity((TextDisplayEntity) e);
					if (entity.getType() == MineBoxEntity.MineBoxEntityType.MINER) {
						targets.add(entity);
					}
				}
			}
	
			System.out.println("Found " + targets.size() + " MineBox entities.");
		}
	
		public MineBoxEntity findClosestTarget() {
			return targets.stream()
					.filter(MineBoxEntity::isHarvestable)
					.min(Comparator.comparingDouble(target -> target.getPos().getSquaredDistance(MC.player.getPos())))
					.orElse(null);
		}
	}
	
	class MineBoxPathFinder {
	
		private PathFinder pathFinder;
		private PathProcessor processor;

		public boolean isDone() {
			if(processor == null) return false;
			return processor.isDone();
		}
	
		public void updatePathfinding(BlockPos targetPos) {
			if (pathFinder == null) {
				pathFinder = new PathFinder(targetPos);
			}
	
			if (!pathFinder.isDone()) {
				PathProcessor.lockControls();

				pathFinder.think();

				if (!pathFinder.isDone()) {
					if (pathFinder.isFailed()) {
						pathFinder = null;
					}
					return;
				}

				pathFinder.formatPath();

				// Set processor
				processor = pathFinder.getProcessor();

				return;
			}
	
			if (processor != null && !pathFinder.isPathStillValid(processor.getIndex())) {
				pathFinder = new PathFinder(pathFinder.getGoal());
				return;
			}
	
			if (processor != null) {
				processor.process();
				if (processor.isDone()) {
					pathFinder = null;
				}
			}
		}
	
		public boolean hasPath() {
			return pathFinder != null && processor != null;
		}
	
		public void renderPath(MatrixStack matrixStack, boolean debugMode, boolean depthTest) {
			if (pathFinder != null) {
				pathFinder.renderPath(matrixStack, debugMode, depthTest);
			}
		}

		public void reSet() {
			pathFinder = null;
			processor = null;
		}
	}
}
