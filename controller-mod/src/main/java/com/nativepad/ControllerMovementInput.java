package com.nativepad;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MovementInputFromOptions;

/** Vanilla keyboard movement, overridden by the left stick whenever it's pushed past the deadzone. */
public final class ControllerMovementInput extends MovementInputFromOptions {

    public ControllerMovementInput(GameSettings settings) {
        super(settings);
    }

    @Override
    public void updatePlayerMoveState() {
        super.updatePlayerMoveState();
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
