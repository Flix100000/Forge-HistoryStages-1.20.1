package net.astr0.historystages.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The StageManager object is the central class for interacting with the HistoryStages API.
 * The StageManager provides access to checking, locking and unlocking both individual and global stages.
 * To get a reference to the StageManager, use {@link HistoryStagesAPI#getStageManager()}.
 * <p>
 * This interface defines the StageManager API methods available for client mods to call.
 * These methods should only be called from the server. Calling these methods on the client is undefined.
 */
public interface IStageManager {

    /**
     * Check if a stage has been unlocked by the player
     * @param player the player to check
     * @param stage the stage to check
     * @return true if the player has unlocked the provided stage
     */
    boolean isStageUnlockedForPlayer(ServerPlayer player, String stage);

    /**
     * Check if a stage has been unlocked globally
     * @param stage the stage to check
     * @return true if the stage has been unlocked for all players
     */
    boolean isStageUnlockedGlobally(String stage);


    /**
     * Unlock the provided stage for the provided player
     */
    void unlockStageForPlayer(ServerPlayer player, String stage);

    /**
     * Unlock the provided stage for all players
     */
    void unlockStageGlobally(String stage);

    /**
     * Lock the provided stage for the provided player
     */
    void lockStageForPlayer(ServerPlayer player, String stage);

    /**
     * Lock the provided stage for all players
     */
    void lockStageGlobally(String stage);

}
