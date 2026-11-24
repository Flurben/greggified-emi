package com.greggifiedemi;

import org.slf4j.Logger;

import com.greggifiedemi.network.ChatLinks;
import com.mojang.logging.LogUtils;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(GreggifiedEmi.MOD_ID)
public class GreggifiedEmi {

    public static final String MOD_ID = "greggifiedemi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GreggifiedEmi() {
        ChatLinks.init();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientSetup::init);
    }

}
