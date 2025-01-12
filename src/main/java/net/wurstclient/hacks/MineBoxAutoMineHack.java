package net.wurstclient.hacks;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.ai.PathFinder;
import net.wurstclient.ai.PathProcessor;
import net.wurstclient.commands.PathCmd;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filters.FilterInvisibleSetting;
import net.wurstclient.settings.filters.FilterSleepingSetting;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;

@SearchTags({"MineBox", "mine box", "MineBoxAutoMine", "mine box auto mine"})
public final class MineBoxAutoMineHack extends Hack
	implements UpdateListener, RenderListener, HandleInputListener
{
	
	private MineBoxEntity currentTarget;
	private MineBoxEntityFinder entityFinder;
	private MineBoxPathFinder pathFinder;
	
	private final CheckboxSetting damageIndicator =
		new CheckboxSetting("Damage Indicator", true);

	private final SwingHandSetting swingHand = new SwingHandSetting(
		SwingHandSetting.genericCombatDescription(this), SwingHand.CLIENT);
	
	private final SliderSetting range = new SliderSetting("Range",
		"Determines how far Killaura will reach to attack entities.\n"
			+ "Anything that is further away than the specified value will not be attacked.",
		3, 1, 10, 0.05, ValueDisplay.DECIMAL);
	
	private final SliderSetting peopleSearchRange = new SliderSetting(
		"Range To search for people",
		"Determines how far to search for people to pause the hack.\n"
			+ "Anything that is further away than the specified value will not be considered.",
		25, 1, 100, 1, ValueDisplay.DECIMAL);
	
	private final SliderSetting entitySearchRange =
		new SliderSetting("How far to search for minable entities", 25, 1, 100,
			1, ValueDisplay.DECIMAL);
	
	private final AttackSpeedSliderSetting speed =
		new AttackSpeedSliderSetting();
	
	private final SliderSetting speedRandMS =
		new SliderSetting("Speed randomization",
			"Helps you bypass anti-cheat plugins by varying the delay between"
				+ " attacks.\n\n" + "\u00b1100ms is recommended for Vulcan.\n\n"
				+ "0 (off) is fine for NoCheat+, AAC, Grim, Verus, Spartan, and"
				+ " vanilla servers.",
			100, 0, 1000, 50, ValueDisplay.INTEGER.withPrefix("\u00b1")
				.withSuffix("ms").withLabel(0, "off"));
	
	private final EntityFilterList entityFilters = new EntityFilterList(
		new FilterSleepingSetting("Won't pause for sleeping players.", false),
		new FilterInvisibleSetting("Won't pause invisible players.", false));
	
	public MineBoxAutoMineHack()
	{
		super("MineBoxAutoMine");
		setCategory(Category.FUN);
		addSetting(damageIndicator);
		addSetting(speed);
		addSetting(speedRandMS);
		addSetting(swingHand);
		addSetting(range);
		addSetting(peopleSearchRange);
		addSetting(entitySearchRange);
	}
	
	@Override
	protected void onEnable()
	{
		entityFinder = new MineBoxEntityFinder();
		pathFinder = new MineBoxPathFinder();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(HandleInputListener.class, this);
		EVENTS.add(RenderListener.class, this);
		
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(HandleInputListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		currentTarget = null;
		pathFinder = null;
		entityFinder = null;
		PathProcessor.releaseControls();
	}
	
	@Override
	public void onUpdate()
	{
		speed.updateTimer();
		
		PlayerEntity player = MC.player;
		ClientWorld world = MC.world;
		Stream<AbstractClientPlayerEntity> stream = world.getPlayers()
			.parallelStream().filter(e -> !e.isRemoved() && e.getHealth() > 0)
			.filter(e -> e != player)
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> Math.abs(e.getY() - MC.player.getY()) <= 1e6)
			.filter(e -> MC.player.squaredDistanceTo(e) <= peopleSearchRange
				.getValueSq());
		
		stream = entityFilters.applyTo(stream);
		
		// Pause hack when player detected
		if(!(stream.collect(Collectors.toList()).isEmpty()))
		{
			PathProcessor.releaseControls();
			return;
		}
		
		if(currentTarget == null)
		{
			entityFinder.scan();
			currentTarget = entityFinder.findClosestTarget();
			return;
		}
		
		boolean isCurentTarget = currentTarget.matchesPlayerName();
		
		if(!pathFinder.isDone())
		{
			pathFinder.updatePathfinding(currentTarget.getPos());
			
			return;
		}
		
		PathProcessor.releaseControls();
		
		if(pathFinder.isDone() && !isCurentTarget)
		{
			entityFinder.scan();
			MineBoxEntity tempCurrentClosestTarget =
				entityFinder.findClosestTarget();
			
			if(tempCurrentClosestTarget != null && isEqualPos(
				currentTarget.getPos(), tempCurrentClosestTarget.getPos()))
			{
				currentTarget.lookAt();
				currentTarget.setHitBox();
				
				return;
			}
		}
		
		if(isCurentTarget)
		{
			currentTarget.setHitBox();
			
			if(currentTarget.getHitBox() == null)
			{
				pathFinder.reSet();
			}
			
			return;
		}
		
		if(!isCurentTarget)
		{
			pathFinder.reSet();
			currentTarget = null;
		}
	}
	
	private boolean isEqualPos(BlockPos pos1, BlockPos pos2)
	{
		return pos1.getX() == pos2.getX() && pos1.getY() == pos2.getY()
			&& pos1.getZ() == pos2.getZ();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(pathFinder != null && pathFinder.hasPath())
		{
			PathCmd pathCmd = WURST.getCmds().pathCmd;
			pathFinder.renderPath(matrixStack, pathCmd.isDebugMode(),
				pathCmd.isDepthTest());
		}
		
		if(currentTarget == null || !damageIndicator.isChecked())
			return;
		if(currentTarget.getHitBox() == null)
			return;
		
		// GL settings
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		
		matrixStack.push();
		
		RegionPos region = RenderUtils.getCameraRegion();
		RenderUtils.applyRegionalRenderOffset(matrixStack, region);
		
		Box box = new Box(BlockPos.ORIGIN);
		
		float red = 2F;
		float green = 2 - red;
		
		Vec3d lerpedPos =
			EntityUtils.getLerpedPos(currentTarget.getHitBox(), partialTicks)
				.subtract(region.toVec3d());
		matrixStack.translate(lerpedPos.x, lerpedPos.y, lerpedPos.z);
		
		matrixStack.translate(0, 0.05, 0);
		matrixStack.scale(currentTarget.getHitBox().getWidth(),
			currentTarget.getHitBox().getHeight(),
			currentTarget.getHitBox().getWidth());
		matrixStack.translate(-0.5, 0, -0.5);
		
		RenderSystem.setShader(ShaderProgramKeys.POSITION);
		
		RenderSystem.setShaderColor(red, green, 0, 0.25F);
		RenderUtils.drawSolidBox(box, matrixStack);
		
		RenderSystem.setShaderColor(red, green, 0, 0.5F);
		RenderUtils.drawOutlinedBox(box, matrixStack);
		
		matrixStack.pop();
		
		// GL resets
		RenderSystem.setShaderColor(1, 1, 1, 1);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);
	}
	
	@Override
	public void onHandleInput()
	{
		if(currentTarget == null)
			return;
		if(currentTarget.getHitBox() == null)
			return;
		if(!speed.isTimeToAttack())
			return;
		
		MC.interactionManager.attackEntity(MC.player,
			currentTarget.getHitBox());
		swingHand.swing(Hand.MAIN_HAND);
		
		currentTarget.clearHitBox();
		speed.resetTimer(speedRandMS.getValue());
	}
	
	public class MineBoxEntity
	{
		private static final Predicate<Entity> IS_VALID_INTERACT_ENTITY =
			e -> e instanceof InteractionEntity;
		private final BlockPos pos;
		private final boolean isHarvestable;
		private final MineBoxEntityType type;
		private final TextDisplayEntity entity;
		private Entity hitBox;
		private Direction facing;
		
		public enum MineBoxEntityType
		{
			ALCHEMIST,
			LUMBERJACK,
			MINER,
			FISHERMAN,
			UNKNOWN
		}
		
		public MineBoxEntity(TextDisplayEntity entity)
		{
			this.facing = entity.getFacing().getOpposite();
			this.entity = entity;
			this.pos = entity.getBlockPos();
			String text = entity.getText().getString();
			this.type = parseType(text);
			
			// Harvestable condition: text color is not black
			this.isHarvestable = entity.getText().getStyle().getColor() == null
				|| !"#000000".equals(
					entity.getText().getStyle().getColor().getHexCode());
		}
		
		private MineBoxEntityType parseType(String text)
		{
			if(text.contains("Alchemist"))
			{
				return MineBoxEntityType.ALCHEMIST;
			}else if(text.contains("Lumberjack"))
			{
				return MineBoxEntityType.LUMBERJACK;
			}else if(text.contains("Miner"))
			{
				return MineBoxEntityType.MINER;
			}else if(text.contains("Fisherman"))
			{
				return MineBoxEntityType.FISHERMAN;
			}else
			{
				return MineBoxEntityType.UNKNOWN;
			}
		}
		
		public void lookAt()
		{
			if(this.facing.asRotation() != MC.player.getYaw())
			{
				MC.player.setYaw(this.facing.asRotation());
			}
		}
		
		public BlockPos getPos()
		{
			Block block = MC.world.getBlockState(pos).getBlock();
			int y = pos.getY();
			int x = pos.getX();
			int z = pos.getZ();
			// Loop blocks downwards (in Y) untill solid block is found
			while(block instanceof AirBlock)
			{
				block =
					MC.world.getBlockState(new BlockPos(x, y, z)).getBlock();
				y--;
			}
			
			return new BlockPos(x, y + 2, z);
		}
		
		public boolean isHarvestable()
		{
			return isHarvestable;
		}
		
		public Entity getHitBox()
		{
			return hitBox;
		}
		
		public void clearHitBox()
		{
			this.hitBox = null;
		}
		
		public void setHitBox()
		{
			double rangeSq = range.getValueSq();
			Entity smallest = null;
			double smallestSize = 0;
			
			Stream<Entity> stream =
				StreamSupport.stream(MC.world.getEntities().spliterator(), true)
					.filter(IS_VALID_INTERACT_ENTITY)
					.filter(e -> MC.player.squaredDistanceTo(e) <= rangeSq);
			;
			
			for(Entity e : stream.collect(Collectors.toList()))
			{
				if(e instanceof InteractionEntity)
				{
					if(smallestSize == 0)
					{
						smallest = e;
						smallestSize = e.getBoundingBox().getLengthX();
					}else if(e.getBoundingBox().getLengthX() < smallestSize)
					{
						smallest = e;
						smallestSize = e.getBoundingBox().getLengthX();
					}
				}
			}
			
			this.hitBox = smallest;
		}
		
		public boolean matchesPlayerName()
		{
			return entity.getText().getString()
				.contains(MC.player.getDisplayName().getString());
		}
		
		public MineBoxEntityType getType()
		{
			return type;
		}
	}
	
	class MineBoxEntityFinder
	{
		
		private final List<MineBoxEntity> targets = new ArrayList<>();
		private final MinecraftClient MC = MinecraftClient.getInstance();
		
		private static final Predicate<Entity> IS_VALID_TEXT_ENTITY =
			e -> e instanceof TextDisplayEntity;
		
		public void scan()
		{
			Stream<Entity> stream =
				StreamSupport.stream(MC.world.getEntities().spliterator(), true)
					.filter(e -> MC.player
						.squaredDistanceTo(e) <= entitySearchRange.getValueSq())
					.filter(IS_VALID_TEXT_ENTITY);
			
			targets.clear();
			for(Entity e : stream.collect(Collectors.toList()))
			{
				if(e instanceof TextDisplayEntity)
				{
					MineBoxEntity entity =
						new MineBoxEntity((TextDisplayEntity)e);
					if(entity
						.getType() == MineBoxEntity.MineBoxEntityType.MINER)
					{
						targets.add(entity);
					}
				}
			}
		}
		
		public MineBoxEntity findClosestTarget()
		{
			return targets.stream().filter(MineBoxEntity::isHarvestable)
				.min(Comparator.comparingDouble(target -> target.getPos()
					.getSquaredDistance(MC.player.getPos())))
				.orElse(null);
		}
	}
	
	class MineBoxPathFinder
	{
		
		private PathFinder pathFinder;
		private PathProcessor processor;
		
		public boolean isDone()
		{
			if(processor == null)
				return false;
			return processor.isDone()
				&& currentTarget.getPos() != MC.player.getBlockPos();
		}
		
		public void updatePathfinding(BlockPos targetPos)
		{
			if(pathFinder == null)
			{
				pathFinder = new PathFinder(targetPos);
			}
			
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
				
				// Set processor
				processor = pathFinder.getProcessor();
				
				return;
			}
			
			if(processor != null
				&& !pathFinder.isPathStillValid(processor.getIndex()))
			{
				pathFinder = new PathFinder(pathFinder.getGoal());
				return;
			}
			
			if(processor != null)
			{
				processor.process();
				if(processor.isDone())
				{
					pathFinder = null;
				}
			}
		}
		
		public boolean hasPath()
		{
			return pathFinder != null && processor != null;
		}
		
		public void renderPath(MatrixStack matrixStack, boolean debugMode,
			boolean depthTest)
		{
			if(pathFinder != null)
			{
				pathFinder.renderPath(matrixStack, debugMode, depthTest);
			}
		}
		
		public void reSet()
		{
			pathFinder = null;
			processor = null;
		}
	}
}
