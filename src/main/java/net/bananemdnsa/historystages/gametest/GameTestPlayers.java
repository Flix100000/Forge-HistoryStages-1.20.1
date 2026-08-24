package net.bananemdnsa.historystages.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A {@link ServerPlayer} for a test to check things against.
 *
 * <p><strong>Not</strong> {@code GameTestHelper.makeMockServerPlayerInLevel()}, and the reason is
 * worth keeping: that helper runs the full login path — {@code CommonListenerCookie.createInitial}
 * and the {@code PlayerLoggedIn} event — and the dev runtime has FTB Quests, FTB Teams and Jade on
 * it, because this mod compiles against them. FTB Quests answers a login by sending the player a
 * packet, the fake player has no connection, and the test dies with
 * {@code Payload ftbquests:sync_quests_message may not be sent to the client}.
 *
 * <p>That failure says nothing about HistoryStages. The constructor below builds the player
 * directly and fires no events, which is also the more honest test: what is under examination is
 * the dependency checker, not the login flow.
 */
final class GameTestPlayers {

    private GameTestPlayers() {}

    /** A fresh player in the test's own level. Empty inventory, no XP, no stats. */
    static ServerPlayer create(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "gametest");
        return new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
    }
}
