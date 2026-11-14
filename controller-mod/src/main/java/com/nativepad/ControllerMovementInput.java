package com.nativepad;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/** Vanilla keyboard movement, overridden by the left stick whenever it's pushed past the deadzone. */
public final class ControllerMovementInput extends MovementInputFromOptions {

    private static final Field SPRINT_TOGGLE_TIMER =
            ReflectionHelper.findField(EntityPlayerSP.class, "sprintToggleTimer", "field_71156_d");

    public ControllerMovementInput(GameSettings settings) {
        super(settings);
    }

    @Override
    public void updatePlayerMoveState() {
        super.updatePlayerMoveState();

        // Minecraft starts sprinting when "forward" is pressed twice within 7 ticks. A stick can
        // dip out of "forward" for a moment (e.g. pushing it across), which looked like a double-tap
        // and started sprinting with auto sprint off. While the stick is in use, clear that timer.
        // Double-tapping W on the keyboard still works when the stick isn't being used.
        if (System.currentTimeMillis() - ControllerHandler.stickMovedAt < 500 && !NativePad.config.autoSprint) {
            EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
            if (p != null) {
                try {
                    SPRINT_TOGGLE_TIMER.setInt(p, 0);
                } catch (IllegalAccessException ignored) {
                }
            }
        }

        if (!ControllerHandler.moveActive) return;
        float forward = ControllerHandler.moveForward;
        float strafe = ControllerHandler.moveStrafe;
        if (sneak) {
            forward *= 0.3f;
            strafe *= 0.3f;
        }
        moveForward = forward;
        moveStrafe = strafe;
    }
}
