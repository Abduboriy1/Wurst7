package net.wurstclient.hacks;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.ai.PathFinder;
import net.wurstclient.ai.PathProcessor;
import net.wurstclient.command.CmdException;
import net.wurstclient.commands.PathCmd;
import net.wurstclient.events.MouseUpdateListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.minebox.FindMineBoxEntity;
import net.wurstclient.hacks.minebox.MineBoxEntity;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.EspBoxSizeSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

import com.mojang.blaze3d.systems.RenderSystem;

@SearchTags({"bory test", "BoryTest", "bory tests"})
public final class BoryTestHack extends Hack implements UpdateListener, MouseUpdateListener, RenderListener
{
	private MineBoxEntity currentTarget = null;
	private final ArrayList<Entity> targets = new ArrayList<>();
	private final TargetFinder targetFinder = new TargetFinder();
	private float nextYaw;
	private float nextPitch;
	FindMineBoxEntity mineBoxEntity = new FindMineBoxEntity();

	public PathFinder pathFinder = null;
	public PathProcessor processor;

	private final SliderSetting range =
		new SliderSetting("Range", 5, 1, 6, 0.05, SliderSetting.ValueDisplay.DECIMAL);

	private final EnumSetting<Priority> priority = new EnumSetting<>("Priority",
		"Determines which entity will be attacked first.\n"
			+ "\u00a7lDistance\u00a7r - Attacks the closest entity.\n"
			+ "\u00a7lAngle\u00a7r - Attacks the entity that requires the least head movement.\n"
			+ "\u00a7lHealth\u00a7r - Attacks the weakest entity.",
		Priority.values(), Priority.ANGLE);

	private final SliderSetting fov = new SliderSetting("FOV",
		"Field Of View - how far away from your crosshair an entity can be before it's ignored.\n"
			+ "360\u00b0 = entities can be attacked all around you.",
		360, 30, 360, 10, ValueDisplay.DEGREES);

	private final SliderSetting rotationSpeed =
		new SliderSetting("Rotation Speed", 600, 10, 3600, 10,
			ValueDisplay.DEGREES.withSuffix("/s"));

	private final CheckboxSetting damageIndicator = new CheckboxSetting(
		"Damage indicator",
		"Renders a colored box within the target, inversely proportional to its remaining health.",
		true);
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each player.\n"
			+ "\u00a7lFancy\u00a7r mode shows slightly larger boxes that look better.");

	public BoryTestHack()
	{
		super("BoryTest");
		setCategory(Category.FUN);
		addSetting(range);
		addSetting(rotationSpeed);
		addSetting(priority);
		addSetting(fov);
		addSetting(damageIndicator);
		addSetting(boxSize);
	}

	@Override
	protected void onEnable()
	{	
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(MouseUpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(MouseUpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);

		pathFinder = null;
		processor = null;
		currentTarget = null;
		PathProcessor.releaseControls();
		System.out.println("BORY OFF");
	}

	@Override
	public void onUpdate() {
		if(currentTarget == null) {
			mineBoxEntity.scan();
			currentTarget = mineBoxEntity.findClosestTarget();
		} else {
			System.out.println(currentTarget.getCurrentText());
			
			if(currentTarget.getCurrentText().contains(MC.player.getDisplayName().getString())) {
				Vec3d foundSpot = findReachableParticles();
		
				if (foundSpot != null) {
					hitParticle(foundSpot);
				}
			}

			if(pathFinder == null) {
				pathFinder = new PathFinder(currentTarget.getPos());
			}

			if(pathFinder != null) {
				
				// find path
				if(!pathFinder.isDone())
				{
					PathProcessor.lockControls();
					
					pathFinder.think();
					
					if(!pathFinder.isDone())
					{
						if(pathFinder.isFailed())
						{
							pathFinder = null;
						}
						
						return;
					}
					
					pathFinder.formatPath();
					
					// set processor
					processor = pathFinder.getProcessor();
					

					System.out.println("Done");
					IKeyBinding.get(MC.options.attackKey).simulatePress(true);
					IKeyBinding.get(MC.options.attackKey).simulatePress(false);
				}
				
				// check path
				if(processor != null
					&& !pathFinder.isPathStillValid(processor.getIndex()))
				{
					System.out.println("Updating path...");
					pathFinder = new PathFinder(pathFinder.getGoal());
					return;
				}
				
				// process path
				processor.process();
				
				if(processor.isDone())
					pathFinder = null;
			}
		}
	}

	@Override
	public void onMouseUpdate(MouseUpdateEvent event)
	{
		if(currentTarget == null || MC.player == null)
			return;
		
		int diffYaw = (int)(nextYaw - MC.player.getYaw());
		int diffPitch = (int)(nextPitch - MC.player.getPitch());
		if(MathHelper.abs(diffYaw) < 1 && MathHelper.abs(diffPitch) < 1)
			return;
		
		event.setDeltaX(event.getDefaultDeltaX() + diffYaw);
		event.setDeltaY(event.getDefaultDeltaY() + diffPitch);
	}

	private void hitParticle(Vec3d foundSpot)
	{
		Stream<Entity> stream = StreamSupport.stream(MC.world.getEntities().spliterator(), true)
			.filter(IS_ENTITY);
	
		double rangeSq = range.getValueSq();
		stream = stream.filter(e -> MC.player.squaredDistanceTo(e) <= rangeSq);

		currentTarget = null;
		
		if(fov.getValue() < 360.0)
			stream = stream.filter(e -> RotationUtils.getAngleToLookVec(
				e.getBoundingBox().getCenter()) <= fov.getValue() / 2.0);
		
		// currentTarget = stream.min(priority.getSelected().comparator).orElse(null);
		
		if(currentTarget == null)
			return;
		
		// face entity
		if(faceEntityClient(foundSpot)) {
			IKeyBinding.get(MC.options.attackKey).simulatePress(true);
			IKeyBinding.get(MC.options.attackKey).simulatePress(false);
			currentTarget = null;
		}
	}

	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(pathFinder != null) {
			PathCmd pathCmd = WURST.getCmds().pathCmd;
			pathFinder.renderPath(matrixStack, pathCmd.isDebugMode(),
				pathCmd.isDepthTest());
		}
	}

	// @Override
	// public void onRender(MatrixStack matrixStack, float partialTicks)
	// {
	// 	if(targets == null || MC.player == null || !damageIndicator.isChecked())
	// 		return;

	// 	// GL settings
	// 	GL11.glEnable(GL11.GL_BLEND);
	// 	GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
	// 	GL11.glDisable(GL11.GL_DEPTH_TEST);
		
	// 	matrixStack.push();
		
	// 	RegionPos region = RenderUtils.getCameraRegion();
	// 	RenderUtils.applyRegionalRenderOffset(matrixStack, region);

	// 	renderBoxes(matrixStack, partialTicks, region);
		
	// 	matrixStack.pop();
		
	// 	// GL resets
	// 	RenderSystem.setShaderColor(1, 1, 1, 1);
	// 	GL11.glEnable(GL11.GL_DEPTH_TEST);
	// 	GL11.glDisable(GL11.GL_BLEND);
	// }
	
	private void renderBoxes(MatrixStack matrixStack, float partialTicks,
		RegionPos region)
	{
		float extraSize = boxSize.getExtraSize();
		ArrayList<String> UIs = new ArrayList<>();
		
		for(Entity e : targets)
		{
			matrixStack.push();
			
			if(e instanceof TextDisplayEntity)
			{
				TextDisplayEntity textEntity = (TextDisplayEntity) e;
				String text = textEntity.getText().getString();
				UIs.add(text);
			}
			
			Vec3d lerpedPos = EntityUtils.getLerpedPos(e, partialTicks)
				.subtract(region.toVec3d());
			matrixStack.translate(lerpedPos.x, lerpedPos.y, lerpedPos.z);
			
			matrixStack.scale(e.getWidth() + extraSize,
				e.getHeight() + extraSize, e.getWidth() + extraSize);
			
			// set color
			if(WURST.getFriends().contains(e.getName().getString()))
				RenderSystem.setShaderColor(0, 0, 1, 0.5F);
			else
			{
				float f = MC.player.distanceTo(e) / 20F;
				RenderSystem.setShaderColor(2 - f, f, 0, 0.5F);
			}
			
			Box bb = new Box(-0.5, 0, -0.5, 0.5, 1, 0.5);
			RenderUtils.drawOutlinedBox(bb, matrixStack);
			
			matrixStack.pop();
		}
	}

	public static final Predicate<Entity> IS_ENTITY = e -> e instanceof InteractionEntity
		|| e instanceof TextDisplayEntity;


	private boolean faceEntityClient(Vec3d entity)
	{
		Rotation needed = RotationUtils.getNeededRotations(entity);
		
		// turn towards center of boundingBox
		Rotation next = RotationUtils.slowlyTurnTowards(needed, rotationSpeed.getValueI() / 20F);
		nextYaw = next.yaw();
		nextPitch = next.pitch();
		
		// check if facing center
		if(RotationUtils.isAlreadyFacing(needed))
			return true;
		
		return false;
	}

	private Vec3d findReachableParticles()
	{
		// Access the ParticleManager
		ParticleManager particleManager = MC.particleManager;
		Vec3d particleCenter = null;

		try {
			// Access the private particles field using reflection
			Field particlesField = ParticleManager.class.getDeclaredField("particles");
			particlesField.setAccessible(true);

			@SuppressWarnings("unchecked")
			Map<ParticleTextureSheet, Queue<Particle>> particlesMap = 
				(Map<ParticleTextureSheet, Queue<Particle>>) particlesField.get(particleManager);
			
			List<Vec3d> points = new ArrayList<>();
			
			// Iterate through particles and try to "hit" those that are reachable
			for (Queue<Particle> particleQueue : particlesMap.values()) {
				for (Particle particle : particleQueue) {
					if (isGlowParticle(particle) && isParticleReachable(particle)) {
						points.add(particle.getBoundingBox().getCenter());
					}
				}
			}

			if(!points.isEmpty()) {
				particleCenter = findCenter(points);
			}
			
			return particleCenter;

		} catch (NoSuchFieldException | IllegalAccessException e) {
			e.printStackTrace();
		}

		// Return null if no particle was found
		return particleCenter;
	}

	public Vec3d findCenter(List<Vec3d> points) {
		if (points == null || points.isEmpty()) {
			throw new IllegalArgumentException("Point list cannot be null or empty.");
		}
	
		double sumX = 0.0;
		double sumY = 0.0;
		double sumZ = 0.0;
	
		// Sum up all coordinates
		for (Vec3d point : points) {
			sumX += point.x;
			sumY += point.y;
			sumZ += point.z;
		}
	
		// Calculate the average for each coordinate
		double centerX = sumX / points.size();
		double centerY = sumY / points.size();
		double centerZ = sumZ / points.size();

		// Return the center as a new Vec3d
		return new Vec3d(centerX, centerY, centerZ);
	}

	private boolean isGlowParticle(Particle particle)
	{
		return particle.getClass().getSimpleName().contains("GlowParticle");
	}

	private boolean isParticleReachable(Particle particle)
	{
		Vec3d particlePos = particle.getBoundingBox().getCenter();
		BlockPos pos = new BlockPos((int)particlePos.x, (int)particlePos.y, (int)particlePos.z);
		
		// skip over blocks that we can't reach
		BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
		if(params == null || params.distanceSq() > range.getValueSq())
			return false;
		
		return true;
	}

	private enum Priority
	{
		DISTANCE("Distance", e -> MC.player.squaredDistanceTo(e)),
		
		ANGLE("Angle",
			e -> RotationUtils
				.getAngleToLookVec(e.getBoundingBox().getCenter())),
		
		HEALTH("Health", e -> e instanceof LivingEntity
			? ((LivingEntity)e).getHealth() : Integer.MAX_VALUE);
		
		private final String name;
		private final Comparator<Entity> comparator;
		
		private Priority(String name, ToDoubleFunction<Entity> keyExtractor)
		{
			this.name = name;
			comparator = Comparator.comparingDouble(keyExtractor);
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}

	public class TargetFinder {
		private final List<BlockPos> visitedTargets = new ArrayList<>();
		private final Map<BlockPos, Long> respawnTimers = new HashMap<>();
		private final int respawnTime = 60000; // 1 minute in milliseconds
		private BlockPos currentTargetPos = null;
		private Entity currentTarget = null;


		public BlockPos findClosestTarget(List<BlockPos> potentialTargets, BlockPos currentPosition) {
			return potentialTargets.stream()
					.filter(target -> !visitedTargets.contains(target) || hasRespawned(target))
					.min(Comparator.comparingDouble(target -> target.getSquaredDistance(currentPosition)))
					.orElse(null);
		}

		public void markTargetVisited(BlockPos target) {
			visitedTargets.add(target);
			respawnTimers.put(target, System.currentTimeMillis());
		}

		public boolean hasRespawned(BlockPos target) {
			if (!respawnTimers.containsKey(target)) {
				return true;
			}
			long elapsed = System.currentTimeMillis() - respawnTimers.get(target);
			return elapsed >= respawnTime;
		}

		public boolean isAtTarget(BlockPos playerPos, BlockPos target) {
			return playerPos.equals(target);
		}
	}

	// Helper methods
	private List<BlockPos> findMiningTargets() {
		// Logic to find potential mining targets
		return StreamSupport.stream(MC.world.getEntities().spliterator(), true)
				.filter(entity -> entity instanceof TextDisplayEntity)
				.map(entity -> ((TextDisplayEntity) entity).getBlockPos())
				.collect(Collectors.toList());
	}

	private void moveToTarget(BlockPos target) {
		String[] goToCommand = { String.valueOf(target.getX()), String.valueOf(target.getY()), String.valueOf(target.getZ()) };
		
		try {
			if(WURST.getCmds().goToCmd.pathFinder != null) {
				if(!WURST.getCmds().goToCmd.pathFinder.isDone())
				{
					if(WURST.getCmds().goToCmd.pathFinder.isFailed())
					{
						ChatUtils.error("Could not find a path.");
					}
					
					return;
				}
			}

			WURST.getCmds().goToCmd.call(goToCommand);
		} catch (CmdException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void mineBlock(BlockPos target) {
		MC.interactionManager.attackBlock(target, Direction.UP);
	}

	private String getTargetText(int targetId) {
		Entity entity = MC.world.getEntityById(targetId);
		if (entity instanceof TextDisplayEntity) {
			return ((TextDisplayEntity) entity).getText().getString();
		}
		return "";
	}
}

