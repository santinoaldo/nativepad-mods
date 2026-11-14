package com.handicaps;

/**
 * Every fake sound Paranoia can play, each with its own on/off setting and Test button.
 * "Bedwars" sound set: only sounds you actually hear in Bedwars.
 */
enum ParanoiaSound {
    STEPS_WOOL("stepsWool", "Footsteps on wool", "step.cloth"),
    STEPS_STONE("stepsStone", "Footsteps on stone / end stone", "step.stone"),
    STEPS_WOOD("stepsWood", "Footsteps on wood", "step.wood"),
    BRIDGING("bridging", "Someone bridging toward you", "dig.cloth"),
    WOOL_BREAK("woolBreak", "Wool being broken", "dig.cloth"),
    FALL_SMALL("fallSmall", "Small fall damage", "game.player.hurt.fall.small"),
    FALL_BIG("fallBig", "Big fall damage", "game.player.hurt.fall.big"),
    BOW("bow", "Bow shot", "random.bow"),
    TNT("tnt", "TNT fuse", "game.tnt.primed"),
    FIREBALL("fireball", "Fireball", "mob.ghast.fireball"),
    PEARL("pearl", "Ender pearl landing", "mob.endermen.portal"),
    EATING("eating", "Eating (golden apple)", "random.eat"),
    DRINKING("drinking", "Drinking (potion)", "random.drink"),
    FIGHT("fight", "A fight nearby", "game.player.hurt");

    /** Config key, shown label, Minecraft 1.8.9 sound name (all verified to exist). */
    final String key, label, sound;

    ParanoiaSound(String key, String label, String sound) {
        this.key = key;
        this.label = label;
        this.sound = sound;
    }

    static ParanoiaSound byKey(String key) {
        for (ParanoiaSound s : values()) if (s.key.equals(key)) return s;
        return null;
    }
}
