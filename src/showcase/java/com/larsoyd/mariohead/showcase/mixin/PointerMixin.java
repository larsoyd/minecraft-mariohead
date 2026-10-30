package com.larsoyd.mariohead.showcase.mixin;

import com.larsoyd.mariohead.showcase.Pointer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the recording display never composites the system cursor, so menus draw their own
@Mixin(AbstractContainerScreen.class)
public class PointerMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void pointer(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		var mouse = Minecraft.getInstance().mouseHandler;
		float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
		graphics.nextStratum();
		graphics.pose().pushMatrix();
		graphics.pose().scale(1 / scale, 1 / scale);
		graphics.pose().translate((float) mouse.xpos(), (float) mouse.ypos());
		for (int[] run : Pointer.runs()) graphics.fill(run[0], run[1], run[2], run[1] + 1, run[3]);
		graphics.pose().popMatrix();
	}
}
