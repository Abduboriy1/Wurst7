package net.wurstclient.hacks.minebox;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.util.math.BlockPos;

public class MineBoxEntity {
    private final BlockPos pos;
    // private final boolean isHarvestable = false;
    private final int respawnTime = 30000; // 1 minute in milliseconds
    private MineBoxEntityType mineBoxEntityType = MineBoxEntityType.UNKNOWN;       
    TextDisplayEntity entity = null;

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
    
        if (text.contains("Alchemist")) {
            this.mineBoxEntityType = MineBoxEntityType.ALCHEMIST;
        } else if (text.contains("Lumberjack")) {
            this.mineBoxEntityType = MineBoxEntityType.LUMBERJACK;
        } else if(text.contains("Miner")) {
            this.mineBoxEntityType = MineBoxEntityType.MINER;
        } else if(text.contains("Fisherman")) {
            this.mineBoxEntityType = MineBoxEntityType.FISHERMAN;
        }
    }

    public BlockPos getPos() {
        BlockPos ajustedPos = new BlockPos(pos.getX(), pos.getY() - 2, pos.getZ());
        return ajustedPos;
    }

    public String getCurrentText() {
        return entity.getText().getString();
    }

    public MineBoxEntityType getMineBoxEntityType() {
        return mineBoxEntityType;
    }

    public int getRespawnTime() {
        return respawnTime;
    }
}