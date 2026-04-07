package org.kyowa.spotplaying.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(KeyBinding.class)
public class KeyBindingCategoryMixin {
    // Exists to enable mixin infrastructure for custom keybind category via lang file
}
