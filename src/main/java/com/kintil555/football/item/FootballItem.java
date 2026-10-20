package com.kintil555.football.item;

import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Placing this item on a block puts a football entity into the world. */
public class FootballItem extends Item {
    public FootballItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        FootballEntity ball = new FootballEntity(ModEntities.FOOTBALL, serverLevel);
        ball.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        serverLevel.addFreshEntity(ball);

        context.getItemInHand().consume(1, context.getPlayer());
        return InteractionResult.SUCCESS;
    }
}
