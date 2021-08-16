package me.jellysquid.mods.sodium.mixin.collections.entity_filtering;

import net.minecraft.util.ClassInheritanceMultiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.*;

/**
 * Patches {@link ClassInheritanceMultiMap} to improve performance when entities are being queried in the world.
 */
@Mixin(ClassInheritanceMultiMap.class)
public class TypeFilterableListMixin<T> {
    @Shadow
    @Final
    private Class<T> baseClass;

    @Shadow
    @Final
    private Map<Class<?>, List<T>> byClass;

    @Shadow
    @Final
    private List<T> allInstances;

    /**
     * @reason Only perform the slow Class#isAssignableFrom(Class) if a list doesn't exist for the type, otherwise
     * we can assume it's already valid. The slow-path code is moved to a separate method to help the JVM inline this.
     * @author JellySquid
     */
    @SuppressWarnings("unchecked")
    @Overwrite
    public <S> Collection<S> find(Class<S> type) {
        Collection<T> collection = this.byClass.get(type);

        if (collection == null) {
            collection = this.createAllOfType(type);
        }

        return (Collection<S>) Collections.unmodifiableCollection(collection);
    }

    private <S> Collection<T> createAllOfType(Class<S> type) {
        if (!this.baseClass.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Don't know how to search for " + type);
        }

        List<T> list = new ArrayList<>();

        for (T allElement : this.allInstances) {
            if (type.isInstance(allElement)) {
                list.add(allElement);
            }
        }

        this.byClass.put(type, list);

        return list;
    }

    /**
     * @author JellySquid
     * @reason Do not copy the list every call to provide immutability, instead wrap with an unmodifiable type
     */
    @Overwrite
    public List<T> getAllInstances() {
        return Collections.unmodifiableList(this.allInstances);
    }
}
