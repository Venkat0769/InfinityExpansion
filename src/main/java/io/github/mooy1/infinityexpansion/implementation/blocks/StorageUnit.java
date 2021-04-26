package io.github.mooy1.infinityexpansion.implementation.blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.mooy1.infinityexpansion.InfinityExpansion;
import io.github.mooy1.infinityexpansion.categories.Categories;
import io.github.mooy1.infinitylib.items.StackUtils;
import io.github.mooy1.infinitylib.persistence.PersistenceUtils;
import io.github.mooy1.infinitylib.slimefun.abstracts.AbstractContainer;
import io.github.mooy1.infinitylib.slimefun.presets.LorePreset;
import io.github.mooy1.infinitylib.slimefun.presets.MenuPreset;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.SlimefunItem;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.SlimefunItemStack;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import me.mrCookieSlime.Slimefun.cscorelib2.chat.ChatColors;
import me.mrCookieSlime.Slimefun.cscorelib2.collections.Pair;
import me.mrCookieSlime.Slimefun.cscorelib2.item.CustomItem;

/**
 * A block that stored large amounts of 1 item
 *
 * @author Mooy1
 *
 * Thanks to FluffyBear for stuff to learn from
 */
public final class StorageUnit extends AbstractContainer {
    
    /* Menu strings */
    private static final String EMPTY_DISPLAY_NAME = ChatColor.WHITE + "Empty";
    private static final String VOID_EXCESS_TRUE = ChatColors.color("&7Void Excess:&e true");
    private static final String VOID_EXCESS_FALSE = ChatColors.color("&7Void Excess:&e false");

    /* BlockStorage keys */
    private static final String OLD_STORED_ITEM = "storeditem"; // old item key in block data
    private static final String STORED_AMOUNT = "stored"; // amount key in block data
    private static final String VOID_EXCESS = "void_excess";

    /* Namespaced keys */
    private static final NamespacedKey EMPTY_KEY = InfinityExpansion.inst().getKey("empty"); // key for empty item
    private static final NamespacedKey DISPLAY_KEY = InfinityExpansion.inst().getKey("display"); // key for display item
    private static final NamespacedKey OLD_ITEM_KEY = InfinityExpansion.inst().getKey("stored_item"); // old item key in pdc
    private static final NamespacedKey ITEM_KEY = InfinityExpansion.inst().getKey("item"); // item key for item pdc
    private static final NamespacedKey AMOUNT_KEY = InfinityExpansion.inst().getKey("stored"); // amount key for item pdc

    /* Menu slots */
    private static final int INPUT_SLOT = MenuPreset.slot1;
    private static final int DISPLAY_SLOT = MenuPreset.slot2;
    private static final int STATUS_SLOT = DISPLAY_SLOT - 9;
    private static final int OUTPUT_SLOT = MenuPreset.slot3;
    private static final int INTERACT_SLOT = DISPLAY_SLOT + 9;

    /* Menu items */
    private static final ItemStack EMPTY_ITEM = new CustomItem(Material.BARRIER, meta -> {
        meta.setDisplayName(ChatColor.WHITE + "Empty");
        meta.getPersistentDataContainer().set(EMPTY_KEY, PersistentDataType.BYTE, (byte) 1);
    });
    private static final ItemStack INTERACTION_ITEM = new CustomItem(Material.LIME_STAINED_GLASS_PANE,
            "&aQuick Actions",
            "&bLeft Click: &7Withdraw 1 item",
            "&bRight Click: &7Withdraw 1 stack",
            "&bShift Left Click: &7Deposit inventory",
            "&bShift Right Click: &7Withdraw inventory"
    );
    private static final ItemStack LOADING_ITEM = new CustomItem(Material.CYAN_STAINED_GLASS_PANE,
            "&bStatus",
            "&7Loading..."
    );

    /* Instance constants */
    private final Map<Location, StorageCache> caches = new HashMap<>();
    private final int max;

    public StorageUnit(SlimefunItemStack item, int max, ItemStack[] recipe) {
        super(Categories.STORAGE, item, StorageForge.TYPE, recipe);
        this.max = max;

        addItemHandler(new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return true;
            }

            @Override
            public void tick(Block b, SlimefunItem item, Config data) {
                StorageCache cache = StorageUnit.this.caches.get(b.getLocation());
                if (cache != null) {
                    cache.tick(b);
                }
            }
        });
    }

    @Override
    protected void onNewInstance(@Nonnull BlockMenu menu, @Nonnull Block b) {
        if (this.caches.containsKey(b.getLocation())) {
            // TEMP FIX
            return;
        }
        this.caches.put(b.getLocation(), new StorageCache(b, menu));
    }

    @Override
    protected void onBreak(@Nonnull BlockBreakEvent e, @Nonnull BlockMenu menu, @Nonnull Location l) {
        this.caches.remove(l).destroy(e);
    }

    @Override
    protected void onPlace(@Nonnull BlockPlaceEvent e, @Nonnull Block b) {
        Pair<ItemStack, Integer> data = loadFromStack(e.getItemInHand().getItemMeta());
        if (data != null) {
            InfinityExpansion.inst().runSync(() -> {
                StorageCache cache = this.caches.get(b.getLocation());
                cache.load(data.getFirstValue(), data.getFirstValue().getItemMeta());
                cache.setAmount(data.getSecondValue());
            });
        }
    }

    @Override
    protected void setupMenu(@Nonnull BlockMenuPreset blockMenuPreset) {
        MenuPreset.setupBasicMenu(blockMenuPreset);
        blockMenuPreset.addMenuClickHandler(DISPLAY_SLOT, ChestMenuUtils.getEmptyClickHandler());
        blockMenuPreset.addItem(INTERACT_SLOT, INTERACTION_ITEM);
        blockMenuPreset.addItem(STATUS_SLOT, LOADING_ITEM);
    }

    @Nonnull
    @Override
    protected int[] getTransportSlots(@Nonnull DirtyChestMenu dirtyChestMenu, @Nonnull ItemTransportFlow flow, ItemStack itemStack) {
        StorageCache cache = this.caches.get(((BlockMenu) dirtyChestMenu).getLocation());
        if (cache != null) {
            if (flow == ItemTransportFlow.INSERT) {
                // check if input can be stored
                if (cache.isEmpty() || cache.matches(itemStack)) {
                    ItemStack input = dirtyChestMenu.getItemInSlot(INPUT_SLOT);
                    if (input != null && input.getAmount() + itemStack.getAmount() > itemStack.getMaxStackSize()) {
                        // clear the input spot to make room
                        cache.input();
                    }
                    return new int[] {INPUT_SLOT};
                }
            } else if (flow == ItemTransportFlow.WITHDRAW) {
                cache.output(false);
                return new int[] {OUTPUT_SLOT};
            }
        }
        return new int[0];
    }

    public static void transferToStack(@Nonnull ItemStack source, @Nonnull ItemStack target) {
        Pair<ItemStack, Integer> data = loadFromStack(source.getItemMeta());
        if (data != null) {
            target.setItemMeta(saveToStack(target.getItemMeta(), data.getFirstValue(),
                    StackUtils.getDisplayName(data.getFirstValue()), data.getSecondValue()));
        }
    }

    private static ItemMeta saveToStack(ItemMeta meta, ItemStack displayItem, String displayName, int amount) {
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            lore.add(ChatColor.GOLD + "Stored: " + displayName + ChatColor.YELLOW + " x " + amount);
            meta.setLore(lore);
        }
        meta.getPersistentDataContainer().set(ITEM_KEY, PersistenceUtils.ITEM_STACK, displayItem);
        meta.getPersistentDataContainer().set(AMOUNT_KEY, PersistentDataType.INTEGER, amount);
        return meta;
    }

    private static Pair<ItemStack, Integer> loadFromStack(ItemMeta meta) {
        // get amount
        Integer amount = meta.getPersistentDataContainer().get(AMOUNT_KEY, PersistentDataType.INTEGER);
        if (amount != null) {

            // check for old id
            String oldID = meta.getPersistentDataContainer().get(OLD_ITEM_KEY, PersistentDataType.STRING);
            if (oldID != null) {
                ItemStack item = StackUtils.getItemByIDorType(oldID);
                if (item != null) {
                    // add the display key to it
                    ItemMeta update = item.getItemMeta();
                    update.getPersistentDataContainer().set(DISPLAY_KEY, PersistentDataType.BYTE, (byte) 1);
                    item.setItemMeta(update);
                    return new Pair<>(item, amount);
                }
            }

            // get item
            ItemStack item = meta.getPersistentDataContainer().get(ITEM_KEY, PersistenceUtils.ITEM_STACK);
            if (item != null) {
                return new Pair<>(item, amount);
            }
        }
        return null;
    }

    public void reloadCache(Location l) {
        StorageCache cache = this.caches.get(l);
        if (cache != null) {
            cache.reloadData();
        }
    }

    /**
     * Represents a single storage unit with cached data and main functionality
     *
     * @author Mooy1
     */
    private final class StorageCache {

        private final BlockMenu menu;
        private Material material;
        private ItemMeta meta;
        private String displayName;
        private boolean voidExcess;
        private int amount;

        StorageCache(Block block, BlockMenu menu) {

            // load data
            this.menu = menu;
            reloadData();

            if (this.amount == 0) {
                // empty
                this.displayName = EMPTY_DISPLAY_NAME;
                menu.replaceExistingItem(DISPLAY_SLOT, EMPTY_ITEM);
            } else {
                // something is stored
                ItemStack display = menu.getItemInSlot(DISPLAY_SLOT);
                if (display != null) {
                    ItemMeta copy = display.getItemMeta();
                    // fix if they somehow store the empty item
                    if (copy.getPersistentDataContainer().has(EMPTY_KEY, PersistentDataType.BYTE)) {
                        // attempt to recover the correct item from output
                        ItemStack output = menu.getItemInSlot(OUTPUT_SLOT);
                        if (output != null) {
                            setStored(output);
                            menu.replaceExistingItem(OUTPUT_SLOT, null);
                        } else {
                            // no output to recover
                            menu.replaceExistingItem(DISPLAY_SLOT, EMPTY_ITEM);
                            this.displayName = EMPTY_DISPLAY_NAME;
                            this.amount = 0;
                        }
                    } else {
                        // load the item in menu
                        load(display, copy);
                    }
                } else {
                    // attempt to load old data
                    String oldID = BlockStorage.getLocationInfo(block.getLocation(), OLD_STORED_ITEM);
                    if (oldID != null) {
                        InfinityExpansion.inst().runSync(() -> BlockStorage.addBlockInfo(block, OLD_STORED_ITEM, null));
                        ItemStack item = StackUtils.getItemByIDorType(oldID);
                        if (item != null) {
                            load(item, item.getItemMeta());
                        } else {
                            // invalid old id
                            menu.replaceExistingItem(DISPLAY_SLOT, EMPTY_ITEM);
                            this.displayName = EMPTY_DISPLAY_NAME;
                            this.amount = 0;
                        }
                    } else {
                        // no old id
                        menu.replaceExistingItem(DISPLAY_SLOT, EMPTY_ITEM);
                        this.displayName = EMPTY_DISPLAY_NAME;
                        this.amount = 0;
                    }
                }
            }

            // menu click handlers
            menu.addMenuClickHandler(STATUS_SLOT, (p, slot, item, action) -> {
                voidExcessHandler();
                return false;
            });
            menu.addMenuClickHandler(INTERACT_SLOT, (p, slot, item, action) -> {
                interactHandler(p, action);
                return false;
            });

            // load status slot
            updateStatus();
        }
        
        private void reloadData() {
            Config config = BlockStorage.getLocationInfo(this.menu.getLocation());
            String amt = config.getString(STORED_AMOUNT);
            if (amt == null) {
                this.amount = 0;
                InfinityExpansion.inst().runSync(() -> BlockStorage.addBlockInfo(this.menu.getLocation(), STORED_AMOUNT, "0"));
            } else {
                this.amount = Integer.parseInt(amt);
            }
            this.voidExcess = "true".equals(config.getString(VOID_EXCESS));
        }
        
        private void load(ItemStack stored, ItemMeta copy) {
            this.menu.replaceExistingItem(DISPLAY_SLOT, stored);

            // remove the display key from copy
            copy.getPersistentDataContainer().remove(DISPLAY_KEY);

            // check if the copy has anything besides the display key
            if (copy.equals(Bukkit.getItemFactory().getItemMeta(stored.getType()))) {
                this.meta = null;
                this.displayName = StackUtils.getInternalName(stored);
            } else {
                this.meta = copy;
                this.displayName = StackUtils.getDisplayName(stored, copy);
            }
            this.material = stored.getType();
        }

        private void setAmount(int amount) {
            this.amount = amount;
            BlockStorage.addBlockInfo(this.menu.getLocation(), STORED_AMOUNT, String.valueOf(amount));
        }

        private void destroy(BlockBreakEvent e) {
            if (this.amount != 0) {
                e.setDropItems(false);

                // add output slot
                ItemStack output = this.menu.getItemInSlot(OUTPUT_SLOT);
                if (output != null && matches(output)) {
                    int add = Math.min(StorageUnit.this.max - this.amount, output.getAmount());
                    if (add != 0) {
                        this.amount += add;
                        output.setAmount(output.getAmount() - add);
                    }
                }

                ItemStack drop = StorageUnit.this.getItem().clone();
                drop.setItemMeta(saveToStack(drop.getItemMeta(), this.menu.getItemInSlot(DISPLAY_SLOT), this.displayName, this.amount));
                e.getPlayer().sendMessage(ChatColor.GREEN + "Stored items transferred to dropped item");
                e.getBlock().getWorld().dropItemNaturally(this.menu.getLocation(), drop);
            }

            this.menu.dropItems(this.menu.getLocation(), INPUT_SLOT, OUTPUT_SLOT);
        }

        private void input() {
            ItemStack input = this.menu.getItemInSlot(INPUT_SLOT);
            if (input == null) {
                return;
            }
            if (this.amount == 0) {
                // set the stored item to input
                setAmount(input.getAmount());
                setStored(input);
                this.menu.replaceExistingItem(INPUT_SLOT, null, false);
            } else if (matches(input)) {
                if (this.voidExcess) {
                    // input and void excess
                    if (this.amount < StorageUnit.this.max) {
                        setAmount(Math.min(this.amount + input.getAmount(), StorageUnit.this.max));
                    }
                    input.setAmount(0);
                } else if (this.amount < StorageUnit.this.max) {
                    // input as much as possible
                    if (input.getAmount() + this.amount >= StorageUnit.this.max) {
                        // last item
                        input.setAmount(input.getAmount() - (StorageUnit.this.max - this.amount));
                        setAmount(StorageUnit.this.max);
                    } else {
                        setAmount(this.amount + input.getAmount());
                        input.setAmount(0);
                    }
                }
            }
        }

        private void output(boolean partial) {
            if (this.amount != 0) {
                ItemStack outputSlot = this.menu.getItemInSlot(OUTPUT_SLOT);
                if (outputSlot == null) {
                    if (this.amount == 1) {
                        this.menu.replaceExistingItem(OUTPUT_SLOT, createItem(1), false);
                        setEmpty();
                    } else {
                        int amt = Math.min(this.material.getMaxStackSize(), this.amount - 1);
                        this.menu.replaceExistingItem(OUTPUT_SLOT, createItem(amt), false);
                        setAmount(this.amount - amt);
                    }
                } else if (partial && this.amount != 1) {
                    int amt = Math.min(this.material.getMaxStackSize() - outputSlot.getAmount(), this.amount - 1);
                    if (amt != 0 && matches(outputSlot)) {
                        outputSlot.setAmount(outputSlot.getAmount() + amt);
                        setAmount(this.amount - amt);
                    }
                }
            }
        }

        private void tick(Block block) {

            // input output
            input();
            output(true);

            // status
            if (this.menu.hasViewer()) {
                updateStatus();
            }

            // sings
            if ((InfinityExpansion.inst().getGlobalTick() & 15) == 0) {
                Block check = block.getRelative(0, 1, 0);
                if (SlimefunTag.SIGNS.isTagged(check.getType())
                        || checkWallSign(check = block.getRelative(1, 0, 0), block)
                        || checkWallSign(check = block.getRelative(-1, 0, 0), block)
                        || checkWallSign(check = block.getRelative(0, 0, 1), block)
                        || checkWallSign(check = block.getRelative(0, 0, -1), block)
                ) {
                    Sign sign = (Sign) check.getState();
                    sign.setLine(0, ChatColor.GRAY + "--------------");
                    sign.setLine(1, this.displayName);
                    sign.setLine(2, ChatColor.YELLOW.toString() + this.amount);
                    sign.setLine(3, ChatColor.GRAY + "--------------");
                    sign.update();
                }
            }
        }

        private void updateStatus() {
            this.menu.replaceExistingItem(STATUS_SLOT, new CustomItem(Material.CYAN_STAINED_GLASS_PANE, meta -> {
                meta.setDisplayName(ChatColor.AQUA + "Status");
                List<String> lore = new ArrayList<>();
                if (this.amount == 0) {
                    lore.add(ChatColors.color("&6Stored: &e0 / " + LorePreset.format(StorageUnit.this.max) + " &7(0%)"));
                } else {
                    lore.add(ChatColors.color("&6Stored: &e" + LorePreset.format(this.amount)
                            + " / " + LorePreset.format(StorageUnit.this.max) + " &7(" + (100 * this.amount) / StorageUnit.this.max + "%)"
                    ));
                }
                lore.add(this.voidExcess ? VOID_EXCESS_TRUE : VOID_EXCESS_FALSE);
                lore.add(ChatColor.GRAY + "(Click to toggle)");
                meta.setLore(lore);
            }), false);
        }

        private boolean checkWallSign(Block sign, Block block) {
            return SlimefunTag.WALL_SIGNS.isTagged(sign.getType())
                    && sign.getRelative(((WallSign) sign.getBlockData()).getFacing().getOppositeFace()).equals(block);
        }

        private void interactHandler(Player p, ClickAction action) {
            if (this.amount == 1) {
                if (action.isShiftClicked() && !action.isRightClicked()) {
                    depositAll(p);
                } else {
                    withdrawLast(p);
                }
            } else if (this.amount != 0) {
                if (action.isRightClicked()) {
                    if (action.isShiftClicked()) {
                        withdraw(p, this.amount - 1);
                    } else {
                        withdraw(p, Math.min(this.material.getMaxStackSize(), this.amount - 1));
                    }
                } else {
                    if (action.isShiftClicked()) {
                        depositAll(p);
                    } else {
                        withdraw(p, 1);
                    }
                }
            }
        }

        private void voidExcessHandler() {
            this.voidExcess = !this.voidExcess;
            BlockStorage.addBlockInfo(this.menu.getLocation(), VOID_EXCESS, this.voidExcess ? "true" : null);
            ItemStack item = this.menu.getItemInSlot(STATUS_SLOT);
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.getLore();
            lore.set(1, this.voidExcess ? VOID_EXCESS_TRUE : VOID_EXCESS_FALSE);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        private void setStored(ItemStack input) {
            if (input.hasItemMeta()) {
                this.meta = input.getItemMeta();
                this.displayName = StackUtils.getDisplayName(input, this.meta);
            } else {
                this.meta = null;
                this.displayName = StackUtils.getInternalName(input);
            }
            this.material = input.getType();

            // add the display key to the display input and set amount 1
            ItemMeta meta = input.getItemMeta();
            meta.getPersistentDataContainer().set(DISPLAY_KEY, PersistentDataType.BYTE, (byte) 1);
            input.setItemMeta(meta);
            input.setAmount(1);

            this.menu.replaceExistingItem(DISPLAY_SLOT, input);
        }

        private void setEmpty() {
            this.displayName = EMPTY_DISPLAY_NAME;
            this.meta = null;
            this.material = null;
            this.menu.replaceExistingItem(DISPLAY_SLOT, EMPTY_ITEM);
            setAmount(0);
        }

        private boolean isEmpty() {
            return this.amount == 0;
        }

        private boolean matches(ItemStack item) {
            return item.getType() == this.material
                    && item.hasItemMeta() == (this.meta != null)
                    && (this.meta == null || this.meta.equals(item.getItemMeta()));
        }

        private ItemStack createItem(int amount) {
            ItemStack item = new ItemStack(this.material, amount);
            if (this.meta != null) {
                item.setItemMeta(this.meta);
            }
            return item;
        }

        private void withdraw(Player p, int withdraw) {
            ItemStack remaining = p.getInventory().addItem(createItem(withdraw)).get(0);
            if (remaining != null) {
                if (remaining.getAmount() != withdraw) {
                    setAmount(this.amount - withdraw + remaining.getAmount());
                }
            } else {
                setAmount(this.amount - withdraw);
            }
        }

        private void withdrawLast(Player p) {
            if (p.getInventory().addItem(createItem(1)).get(0) == null) {
                setEmpty();
            }
        }

        private void depositAll(Player p) {
            if (this.amount < StorageUnit.this.max) {
                int amount = this.amount;
                for (ItemStack item : p.getInventory().getStorageContents()) {
                    if (item != null && matches(item)) {
                        if (item.getAmount() + amount >= StorageUnit.this.max) {
                            // last item
                            item.setAmount(item.getAmount() - (StorageUnit.this.max - amount));
                            amount = StorageUnit.this.max;
                            break;
                        } else {
                            amount += item.getAmount();
                            item.setAmount(0);
                        }
                    }
                }
                if (amount != this.amount) {
                    setAmount(amount);
                }
            }
        }

    }

}
