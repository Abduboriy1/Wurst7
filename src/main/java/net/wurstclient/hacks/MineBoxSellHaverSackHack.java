package net.wurstclient.hacks;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.wurstclient.Category;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IClientPlayerInteractionManager;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

    // TODO 
    // ✅ 1. Check if their is a sack with items in it 
    // 2. If items in sack move it to spot one and shift click it
    // 3. Then find item in the open inventory that contains text "Empty the Bag"
    // 4. pick up the "Empty the Bag" item from its slot and let go
    // 5. close the inventory
    // 6. in chat type /shops and find item in inventory that open called "Farm Shop" click on it
    // 7. find the item in the inventory that says the sack holding item (Wheat) and click on it
    // 8. then in the inventory that opens up select and drop item containing text "Sell all"
    // 9. close the inventory
    // 10. repeat for all sacks in inventory that contain items

    // Make method to pick up and let go of an item in the inventory by slot number

@SearchTags({"MineBox", "mine box", "MineBoxAutoSell", "mine box auto sell"})
public class MineBoxSellHaverSackHack extends Hack implements UpdateListener {

    private final SliderSetting entitySearchRange = new SliderSetting("How far to search for minable entities", 0, 0, 200,
        1, ValueDisplay.INTEGER);
 
    private List<MineBoxItemSack> sacks = new ArrayList<MineBoxItemSack>();

    private static final String[] SACK_NAMES = {
        "Small Wheat Haversack",
        "Small Sugar Cane Haversack",
    };

    private static final HashMap<String, Long> SACK_MAX_AMOUNT = new HashMap<String, Long>() {{
        put("Small Wheat Haversack", 10000L);
        put("Small Sugar Cane Haversack", 20000L);
    }};

    private static final HashMap<Integer, Integer> INV_MAP = new HashMap<Integer, Integer>() {{
        put(0, 36);
        put(1, 37);
        put(2, 38);
        put(3, 39);
        put(4, 40);
        put(5, 41);
        put(6, 42);
        put(7, 43);
        put(8, 44);
        put(9, 45);
    }};

    public MineBoxSellHaverSackHack() {
        super("MineBoxSellHaverSack");
        setCategory(Category.FUN);

        addSetting(entitySearchRange);
    }

    @Override
    protected void onEnable() {
        EVENTS.add(UpdateListener.class, this);

    }

    @Override
    protected void onDisable() {
        EVENTS.remove(UpdateListener.class, this);
    }

    @Override
    public void onUpdate() {
        findAllSacksFromInventory();
        boolean hasSacks = sacks.size() > 0;
        IKeyBinding sneakKey = IKeyBinding.get(MC.options.sneakKey);
        IClientPlayerInteractionManager im = IMC.getInteractionManager();


        if(hasSacks) {
            boolean alreadyAtSlotOne = false;
            for (MineBoxItemSack sack : sacks) {
                if(sack.itemSlot == 0 && sack.amount_inside > 0) {
                    alreadyAtSlotOne = true;
                }
            }

            if(!alreadyAtSlotOne) {
                for (MineBoxItemSack sack : sacks) {
                    if(sack.amount_inside > 0) {
                        im.windowClick_SWAP(getIntMapSlot(sack.itemSlot), 0);
                    }
                }
            } else if(!(MC.currentScreen instanceof HandledScreen)) {
                sneakKey.setPressed(true);
                MC.player.getInventory().setSelectedSlot(0);
                MC.options.useKey.setPressed(true);
                im.rightClickItem();
                MC.options.useKey.setPressed(false);
            }
        }


        if(MC.currentScreen instanceof HandledScreen) {
            sneakKey.resetPressedState();
            // hitEmptyTheBag();
            runInThread(() -> shiftClickSlots());

            EVENTS.remove(UpdateListener.class, this);
        }
    }

    private void waitForDelay(int delay)
	{
		try {
			Thread.sleep(delay);
		} catch(InterruptedException e) {
			throw new RuntimeException(e);
        }
	}


    private void runInThread(Runnable r)
	{
		new Thread(() -> {
			try
			{
				r.run();
				
			}catch(Exception e)
			{
				e.printStackTrace();
			}
		}).start();
	}

    private void shiftClickSlots()
	{
        ScreenHandler screenHandler = MC.player.currentScreenHandler;
        DefaultedList<Slot> slots = screenHandler.slots;
        int syncId = screenHandler.syncId;
        
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            String itemName = slot.getStack().getFormattedName().getString();
            
            if(itemName.contains("Empty the bag")) {
                waitForDelay(500);
                MC.interactionManager.clickSlot(syncId+2, i, 0, SlotActionType.QUICK_MOVE, MC.player);
                waitForDelay(500);
                MC.getNetworkHandler().sendChatCommand("shops");
                waitForDelay(500);
                MC.interactionManager.clickSlot(MC.player.currentScreenHandler.syncId, 3, 0, SlotActionType.QUICK_MOVE, MC.player);
                waitForDelay(500);
                MC.interactionManager.clickSlot(MC.player.currentScreenHandler.syncId, 2, 0, SlotActionType.QUICK_MOVE, MC.player);
                waitForDelay(500);

                for (int j = 0; j < MC.player.currentScreenHandler.slots.size(); j++) {
                    Slot slot1 = MC.player.currentScreenHandler.slots.get(j);
                    String itemName1 = slot1.getStack().getFormattedName().getString();
                    
                    if(itemName1.contains("Sell all")) {
                        MC.interactionManager.clickSlot(MC.player.currentScreenHandler.syncId, j, 0, SlotActionType.QUICK_MOVE, MC.player);
                    }
                }

                break;
            }
        }
	}

    private Integer getIntMapSlot(int slot) {
        // Check if slot is in the map
        if(INV_MAP.containsKey(slot)) {
            return INV_MAP.get(slot);
        }

        return slot;
    }

    private void findAllSacksFromInventory() {
        sacks.clear();
        PlayerInventory inventory = MC.player.getInventory();
        inventory.hashCode();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            String itemName = stack.getFormattedName().getString();
            int slotNumber = i;

            if (!stack.isEmpty() ) {
                for (String sackName : SACK_NAMES) {
                    if (itemName.equals(sackName)) {
                        ComponentMap components = stack.getComponents();
                        components.forEach((j) -> parseSack(j, itemName, slotNumber));
                    }
                }
            }
        }
    }

    public <T> void parseSack(Component<T> component, String itemName, int itemSlot) {
        if(component.type().toString().equals("minecraft:custom_data")) {
            NbtComponent nbtComponent = (NbtComponent) component.value();
            NbtCompound nbt = nbtComponent.copyNbt();
            NbtCompound nbt2 = (NbtCompound) nbt.get("mbitems:persistent");

            if(nbt2 != null) {
                sacks.add(new MineBoxItemSack(nbt2, itemName, itemSlot));
            }
        }
    }

    protected class MineBoxItemSack {

        private String name;
        private long amount_inside;
        private long max_amount;
        private int itemSlot;

        public MineBoxItemSack(NbtCompound nbt, String itemName, int itemSlot) {
            this.name = itemName;
            this.amount_inside = nbt.getLong("mbitems:amount_inside");
            this.max_amount = SACK_MAX_AMOUNT.get(itemName);
            this.itemSlot = itemSlot;
        }
    }

}
