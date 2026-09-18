package com.lukarbonite.autopickup.mixin.client;

import com.lukarbonite.autopickup.client.ClientSyncHandler;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Swallows the server's hidden sync replies ({@code [AP_PERM]}, {@code [AP_GLOBAL]}, ...) before they
 * reach the chat HUD, and hands them to {@link ClientSyncHandler}.
 *
 * <p>Newer versions use Fabric's {@code ClientReceiveMessageEvents} or a Forge chat event; on 1.16.5
 * every chat line funnels through {@code Gui#handleChat}, so one hook here serves both loaders.
 */
@Mixin(Gui.class)
public class ChatInterceptMixin {

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void autopickup_interceptSyncReplies(ChatType type, Component message, UUID sender, CallbackInfo ci) {
        String text = message.getString();
        if (ClientSyncHandler.isSyncMessage(text)) {
            ClientSyncHandler.handleDataResponse(text);
            ci.cancel();
        }
    }
}
