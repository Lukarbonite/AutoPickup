package com.lukarbonite.forge.mixin.client;

import com.lukarbonite.autopickup.client.ClientSyncHandler;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatListener.class)
public class ChatListenerMixin {

    // Forge 47.x has ForgeHooksClient.onClientSystemChat() commented out, so
    // ClientChatReceivedEvent.System never fires. Intercept here instead.
    @Inject(
            method = "handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void autopickup_filterSystemMessage(Component content, boolean overlay, CallbackInfo ci) {
        String text = content.getString();
        if (text.startsWith("[AP_DATA] ") || text.startsWith("[AP_PERM] ") ||
                text.startsWith("[AP_GLOBAL] ") || text.startsWith("[AP_OPEN_GUI]")) {
            ClientSyncHandler.handleDataResponse(text);
            ci.cancel();
        }
    }
}
