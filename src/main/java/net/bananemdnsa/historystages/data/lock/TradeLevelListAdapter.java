package net.bananemdnsa.historystages.data.lock;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes merchant levels as numbers and reads them back as either.
 *
 * <p>The list is held as strings — see {@link TradeLocks} — but {@code "levels": ["4","5"]} in a
 * stage file reads as a mistake to anyone editing it by hand, because a level is a number.
 * Writing numbers and accepting both on the way in costs one small adapter and removes that
 * papercut permanently.
 *
 * <p>Anything that is neither a number nor a string is skipped rather than fatal. A stage file is
 * often hand-written, and the loader reports a malformed value with the stage's name attached,
 * which is far more use than an exception thrown from inside Gson.
 */
public class TradeLevelListAdapter extends TypeAdapter<List<String>> {

    @Override
    public void write(JsonWriter out, List<String> levels) throws IOException {
        if (levels == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (String level : levels) {
            if (level == null) continue;
            try {
                out.value(Long.parseLong(level.trim()));
            } catch (NumberFormatException notANumber) {
                // Kept rather than dropped: throwing away a value the maintainer typed is worse
                // than writing it back as it was and letting the loader complain about it.
                out.value(level);
            }
        }
        out.endArray();
    }

    @Override
    public List<String> read(JsonReader in) throws IOException {
        List<String> levels = new ArrayList<>();
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return levels;
        }
        in.beginArray();
        while (in.hasNext()) {
            switch (in.peek()) {
                case NUMBER, STRING -> levels.add(in.nextString());
                default -> in.skipValue();
            }
        }
        in.endArray();
        return levels;
    }
}
