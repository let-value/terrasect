package net.minecraft.resources;

import com.google.common.collect.MapMaker;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;

public class ResourceKey<T> implements java.lang.Comparable<ResourceKey<?>> {
    private static final ConcurrentMap<ResourceKey.InternKey, ResourceKey<?>> VALUES = new MapMaker().weakValues().makeMap();
    private final Identifier registryName;
    private final Identifier identifier;

    public static <T> Codec<ResourceKey<T>> codec(ResourceKey<? extends Registry<T>> p_195967_) {
        return Identifier.CODEC.xmap(p_466139_ -> create(p_195967_, p_466139_), ResourceKey::identifier);
    }

    public static <T> StreamCodec<ByteBuf, ResourceKey<T>> streamCodec(ResourceKey<? extends Registry<T>> p_320330_) {
        return Identifier.STREAM_CODEC.map(p_466137_ -> create(p_320330_, p_466137_), ResourceKey::identifier);
    }

    public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> p_135786_, Identifier p_467994_) {
        return create(p_135786_.identifier, p_467994_);
    }

    public static <T> ResourceKey<Registry<T>> createRegistryKey(Identifier p_469658_) {
        return create(Registries.ROOT_REGISTRY_NAME, p_469658_);
    }

    private static <T> ResourceKey<T> create(Identifier p_468732_, Identifier p_469992_) {
        return (ResourceKey<T>)VALUES.computeIfAbsent(
            new ResourceKey.InternKey(p_468732_, p_469992_), p_466135_ -> new ResourceKey(p_466135_.registry, p_466135_.identifier)
        );
    }

    private ResourceKey(Identifier p_467626_, Identifier p_467668_) {
        this.registryName = p_467626_;
        this.identifier = p_467668_;
    }

    @Override
    public String toString() {
        return "ResourceKey[" + this.registryName + " / " + this.identifier + "]";
    }

    public boolean isFor(ResourceKey<? extends Registry<?>> p_135784_) {
        return this.registryName.equals(p_135784_.identifier());
    }

    public <E> Optional<ResourceKey<E>> cast(ResourceKey<? extends Registry<E>> p_195976_) {
        return this.isFor(p_195976_) ? Optional.of((ResourceKey<E>)this) : Optional.empty();
    }

    public Identifier identifier() {
        return this.identifier;
    }

    public Identifier registry() {
        return this.registryName;
    }

    public ResourceKey<Registry<T>> registryKey() {
        return createRegistryKey(this.registryName);
    }

    @Override
    public int compareTo(ResourceKey<?> o) {
        int ret = this.registry().compareTo(o.registry());
        if (ret == 0) ret = this.identifier().compareTo(o.identifier());
        return ret;
    }

    record InternKey(Identifier registry, Identifier identifier) {
    }
}
