package com.kintil555.football.client;

import com.kintil555.football.FootballMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Key bindings for ball actions. Kick = left click, pass = left click on a player while holding the ball, catch/juggle = double SPACE. */
public final class FootballKeys {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(FootballMod.MOD_ID, "football"));

    /** Middle click: lift the ball (aim at it); once held, hold to charge and release to throw. */
    public static final KeyMapping HOLD = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key." + FootballMod.MOD_ID + ".hold", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_MIDDLE, CATEGORY));
    public static final KeyMapping JUGGLE = register("juggle", InputConstants.KEY_X);
    public static final KeyMapping FEINT = register("feint", InputConstants.KEY_Z);

    private FootballKeys() {
    }

    private static KeyMapping register(String name, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + FootballMod.MOD_ID + "." + name, defaultKey, CATEGORY));
    }

    public static void init() {
        // Referencing this class triggers the static registrations above.
    }
}
