package net.bananemdnsa.historystages.data;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.bananemdnsa.historystages.data.lock.engine.StageScope;

import org.jetbrains.annotations.Nullable;

/**
 * Guards a stage file from being silently overwritten by a stale in-memory copy.
 *
 * <p>The editor loads a stage once, holds it in memory, and later writes the whole file back out.
 * If somebody edited that file by hand in between, the write would erase the edit without a word.
 * This class answers one question — is it safe to write this stage right now — by comparing the
 * bytes currently on disk against the bytes the server last loaded.
 *
 * <p>Consent to overwrite is bound to the exact file state the player was warned about, not to a
 * plain "already warned" flag. A flag would still be true minutes later and would then wave
 * through a change nobody was shown; binding it to a fingerprint means a second, different hand
 * edit in between is refused and warned about again, while confirming the state actually shown
 * goes through.
 *
 * <p>Deliberately free of Minecraft: no {@code StageManager}, no file I/O, no logging. Every
 * input arrives as an argument, which is what keeps this class on the test runtime classpath —
 * the same split {@code CategoryLockResolver} documents.
 */
public final class StageFileGuard {

    private static final Map<String, String> RECORDED = new ConcurrentHashMap<>();
    private static final Map<String, String> CONSENT = new ConcurrentHashMap<>();

    private StageFileGuard() {}

    /** Records the fingerprint of the bytes the server just loaded (or wrote) for a stage. */
    public static void recordLoaded(String stageId, StageScope scope, byte[] contents) {
        RECORDED.put(key(stageId, scope), fingerprint(contents));
    }

    /**
     * Whether {@code player} may write {@code stageId}/{@code scope} right now, given
     * {@code currentOnDisk} — the bytes currently on disk, or {@code null} if no file exists yet.
     *
     * <p>Refuses whenever the file differs from what the server last loaded and the player has
     * not yet been shown exactly that state. A missing recorded fingerprint is treated the same
     * as a mismatch: a file the server never loaded is precisely a file someone created by hand
     * while it was running, and missing information is not permission.
     */
    public static boolean mayWrite(UUID player, String stageId, StageScope scope,
            @Nullable byte[] currentOnDisk) {
        if (currentOnDisk == null) return true; // nothing on disk to protect

        String current = fingerprint(currentOnDisk);

        if (current.equals(RECORDED.get(key(stageId, scope)))) return true;
        if (current.equals(CONSENT.get(consentKey(player, stageId, scope)))) return true;

        // Refusing: remember the state the warning was about, so confirming it can be
        // recognised - and so a later, different change is warned about again.
        CONSENT.put(consentKey(player, stageId, scope), current);
        return false;
    }

    /**
     * Drops the consent recorded for {@code player} on this stage/scope. Callers invoke this
     * after a write actually succeeds, so a later hand edit is warned about again rather than
     * riding the spent consent. Refreshing the recorded fingerprint after that write is the
     * caller's job, since only the caller knows what actually landed on disk.
     */
    public static void consume(UUID player, String stageId, StageScope scope) {
        CONSENT.remove(consentKey(player, stageId, scope));
    }

    /** Clears all recorded and consented state. Test-only. */
    public static void resetForTesting() {
        RECORDED.clear();
        CONSENT.clear();
    }

    private static String key(String stageId, StageScope scope) {
        return scope + ":" + stageId;
    }

    private static String consentKey(UUID player, String stageId, StageScope scope) {
        return player + ":" + key(stageId, scope);
    }

    private static String fingerprint(byte[] contents) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contents);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JVM implementation.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
