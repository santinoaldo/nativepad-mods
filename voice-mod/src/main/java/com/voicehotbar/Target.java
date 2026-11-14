package com.voicehotbar;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

/** What a voice command selects in the hotbar. */
public enum Target {
    BLOCKS, SWORD, PICKAXE, AXE, SHEARS, BOW, PEARL, ROD, GOLDEN_APPLE, FIRE_CHARGE, WATER, POTION, EGG,
    SLOT_1, SLOT_2, SLOT_3, SLOT_4, SLOT_5, SLOT_6, SLOT_7, SLOT_8, SLOT_9;

    int fixedSlot() {
        return ordinal() >= SLOT_1.ordinal() ? ordinal() - SLOT_1.ordinal() : -1;
    }

    boolean matches(ItemStack stack) {
        if (stack == null) return false;
        Item i = stack.getItem();
        switch (this) {
            case BLOCKS: return i instanceof ItemBlock;
            case SWORD: return i instanceof ItemSword;
            case PICKAXE: return i instanceof ItemPickaxe;
            case AXE: return i instanceof ItemAxe;
            case SHEARS: return i instanceof ItemShears;
            case BOW: return i instanceof ItemBow;
            case PEARL: return i == Items.ender_pearl;
            case ROD: return i instanceof ItemFishingRod;
            case GOLDEN_APPLE: return i == Items.golden_apple;
            case FIRE_CHARGE: return i == Items.fire_charge;
            case WATER: return i == Items.water_bucket;
            case POTION: return i instanceof ItemPotion;
            case EGG: return i == Items.egg;
            default: return false;
        }
    }

    /**
     * Slot to select, or -1 if nothing matches. If the held item already matches, moves on to the
     * next matching slot, so repeating the word cycles through e.g. two stacks of blocks.
     */
    int pick(InventoryPlayer inv) {
        int fixed = fixedSlot();
        if (fixed >= 0) return fixed;
        int cur = inv.currentItem;
        boolean holdingMatch = matches(inv.mainInventory[cur]);
        for (int k = holdingMatch ? 1 : 0; k < 9; k++) {
            int s = (cur + k) % 9;
            if (!holdingMatch) s = k; // not holding one: take the leftmost match
            if (matches(inv.mainInventory[s])) return s;
        }
        return holdingMatch ? cur : -1;
    }

    static Target parse(String s) {
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
