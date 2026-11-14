package com.nativepad;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Remembers the previously selected hotbar slot, however it was changed
 * (controller, number keys, scroll wheel), so one button flips between the two.
 */
final class LastSlot {

    private int current = -1;
    private int previous = -1;

    void observe(EntityPlayer p) {
        int now = p.inventory.currentItem;
        if (current == -1) {
            current = now;
        } else if (now != current) {
            previous = current;
            current = now;
        }
    }

    void swap(EntityPlayer p) {
        observe(p);
        if (previous < 0 || previous == current) return;
        p.inventory.currentItem = previous;
        observe(p);
    }
}
