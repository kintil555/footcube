package com.kintil555.football.client;

import com.kintil555.football.FootballMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Key bindings for every ball action. */
public final class FootballKeys {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(FootballMod.MOD_ID, "football"));

    public static final KeyMapping KICK = register("kick", InputConstants.KEY_R);
    public static final KeyMapping LONG_KICK = register("long_kick", InputConstants.KEY_V);
    public static final KeyMapping THROW = register("throw", InputConstants.KEY_G);
    public static final KeyMapping JUGGLE = register("juggle", InputConstants.KEY_X);
    public static final KeyMapping FEINT = register("feint", InputConstants.KEY_Z);
    public static final KeyMapping DRIBBLE = register("dribble", InputConstants.KEY_C);

    private FootballKeys() {
    }

    private static KeyMapping register(String name, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + FootballMod.MOD_ID + "." + name, defaultKey, CATEGORY));
    }

    public static void init() {
        // Referencing this class triggers the static registrations above.
    }
}
