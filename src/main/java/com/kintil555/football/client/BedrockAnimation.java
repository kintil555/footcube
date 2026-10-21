package com.kintil555.football.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal Bedrock ({@code .animation.json}) reader/sampler: rotation + position channels, linear / catmullrom
 * interpolation, pre/post keyframes and the common easings. Values are in Bedrock units (degrees, pixels).
 */
public final class BedrockAnimation {
    public final double length;
    public final boolean holdOnLast;
    private final Map<String, Bone> bones = new HashMap<>();

    private static final class Key {
        double time;
        double[] pre;
        double[] post;
        boolean catmull;
        String easing = "linear";
    }

    private static final class Channel {
        final List<Key> keys = new ArrayList<>();
    }

    private static final class Bone {
        Channel rotation;
        Channel position;
    }

    private BedrockAnimation(double length, boolean holdOnLast) {
        this.length = length;
        this.holdOnLast = holdOnLast;
    }

    public boolean has(String bone) {
        return this.bones.containsKey(bone);
    }

    public boolean hasRotation(String bone) {
        Bone b = this.bones.get(bone);
        return b != null && b.rotation != null;
    }

    public boolean hasPosition(String bone) {
        Bone b = this.bones.get(bone);
        return b != null && b.position != null;
    }

    /** Rotation in degrees of {@code bone} at {@code time} seconds, or zeros when the bone has no rotation channel. */
    public double[] rotation(String bone, double time) {
        Bone b = this.bones.get(bone);
        return b == null || b.rotation == null ? new double[3] : sample(b.rotation, time);
    }

    /** Position offset in pixels (Bedrock axes: +Y up). */
    public double[] position(String bone, double time) {
        Bone b = this.bones.get(bone);
        return b == null || b.position == null ? new double[3] : sample(b.position, time);
    }

    public static BedrockAnimation load(String resource) {
        try (InputStream in = BedrockAnimation.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject anims = root.getAsJsonObject("animations");
            JsonObject anim = anims.entrySet().iterator().next().getValue().getAsJsonObject();
            boolean hold = anim.has("loop") && anim.get("loop").isJsonPrimitive()
                    && "hold_on_last_frame".equals(anim.get("loop").getAsString());
            BedrockAnimation result = new BedrockAnimation(anim.get("animation_length").getAsDouble(), hold);
            JsonObject bonesJson = anim.getAsJsonObject("bones");
            if (bonesJson != null) {
                for (Map.Entry<String, JsonElement> e : bonesJson.entrySet()) {
                    JsonObject bj = e.getValue().getAsJsonObject();
                    Bone bone = new Bone();
                    bone.rotation = bj.has("rotation") ? parseChannel(bj.get("rotation")) : null;
                    bone.position = bj.has("position") ? parseChannel(bj.get("position")) : null;
                    result.bones.put(e.getKey(), bone);
                }
            }
            return result;
        } catch (Exception ex) {
            return null;
        }
    }

    private static Channel parseChannel(JsonElement el) {
        Channel ch = new Channel();
        if (el.isJsonArray()) {
            ch.keys.add(constKey(vec(el)));
            return ch;
        }
        JsonObject obj = el.getAsJsonObject();
        if (obj.has("vector")) {
            ch.keys.add(parseKey(0.0, obj));
            return ch;
        }
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            double t;
            try {
                t = Double.parseDouble(e.getKey());
            } catch (NumberFormatException ex) {
                continue;
            }
            ch.keys.add(e.getValue().isJsonArray() ? withTime(constKey(vec(e.getValue())), t) : parseKey(t, e.getValue().getAsJsonObject()));
        }
        ch.keys.sort((a, b) -> Double.compare(a.time, b.time));
        return ch;
    }

    private static Key constKey(double[] v) {
        Key k = new Key();
        k.pre = v;
        k.post = v;
        return k;
    }

    private static Key withTime(Key k, double t) {
        k.time = t;
        return k;
    }

    private static Key parseKey(double t, JsonObject o) {
        Key k = new Key();
        k.time = t;
        if (o.has("vector")) {
            k.pre = vec(o.get("vector"));
            k.post = k.pre;
        }
        if (o.has("pre")) {
            k.pre = vec(o.get("pre"));
        }
        if (o.has("post")) {
            k.post = vec(o.get("post"));
            if (k.pre == null) {
                k.pre = k.post;
            }
        }
        if (k.pre == null) {
            k.pre = new double[3];
            k.post = k.pre;
        }
        if (k.post == null) {
            k.post = k.pre;
        }
        k.catmull = o.has("lerp_mode") && "catmullrom".equals(o.get("lerp_mode").getAsString());
        if (o.has("easing")) {
            k.easing = o.get("easing").getAsString();
        }
        return k;
    }

    private static double[] vec(JsonElement el) {
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            return vec(o.has("vector") ? o.get("vector") : o.get("post"));
        }
        JsonArray a = el.getAsJsonArray();
        double[] v = new double[3];
        for (int i = 0; i < 3 && i < a.size(); i++) {
            JsonElement c = a.get(i);
            v[i] = c.isJsonPrimitive() && c.getAsJsonPrimitive().isNumber() ? c.getAsDouble() : 0.0;
        }
        return v;
    }

    private static double[] sample(Channel ch, double t) {
        List<Key> ks = ch.keys;
        int n = ks.size();
        if (n == 0) {
            return new double[3];
        }
        if (t <= ks.get(0).time) {
            return ks.get(0).pre.clone();
        }
        if (t >= ks.get(n - 1).time) {
            return ks.get(n - 1).post.clone();
        }
        int i = 0;
        while (i < n - 2 && t >= ks.get(i + 1).time) {
            i++;
        }
        Key a = ks.get(i);
        Key b = ks.get(i + 1);
        double u = (t - a.time) / (b.time - a.time);
        u = ease(b.easing, u);
        double[] p1 = a.post;
        double[] p2 = b.pre;
        double[] out = new double[3];
        if (a.catmull || b.catmull) {
            double[] p0 = i > 0 ? ks.get(i - 1).post : p1;
            double[] p3 = i + 2 < n ? ks.get(i + 2).pre : p2;
            for (int c = 0; c < 3; c++) {
                out[c] = 0.5 * (2 * p1[c] + (-p0[c] + p2[c]) * u
                        + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * u * u
                        + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * u * u * u);
            }
        } else {
            for (int c = 0; c < 3; c++) {
                out[c] = p1[c] + (p2[c] - p1[c]) * u;
            }
        }
        return out;
    }

    private static double ease(String name, double x) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("linear") || !n.startsWith("ease")) {
            return x;
        }
        String rest = n.substring(4);
        boolean inOut = rest.startsWith("inout");
        boolean out = !inOut && rest.startsWith("out");
        boolean in = !inOut && !out && rest.startsWith("in");
        String fam = inOut ? rest.substring(5) : out ? rest.substring(3) : in ? rest.substring(2) : "";
        if (!in && !out && !inOut) {
            return x;
        }
        if (inOut) {
            return x < 0.5 ? easeIn(fam, x * 2) / 2 : 1 - easeIn(fam, (1 - x) * 2) / 2;
        }
        return out ? 1 - easeIn(fam, 1 - x) : easeIn(fam, x);
    }

    private static double easeIn(String fam, double x) {
        return switch (fam) {
            case "sine" -> 1 - Math.cos(x * Math.PI / 2);
            case "quad" -> x * x;
            case "cubic" -> x * x * x;
            case "quart" -> x * x * x * x;
            case "quint" -> x * x * x * x * x;
            case "expo" -> x == 0 ? 0 : Math.pow(2, 10 * x - 10);
            case "circ" -> 1 - Math.sqrt(1 - x * x);
            default -> x;
        };
    }
}
