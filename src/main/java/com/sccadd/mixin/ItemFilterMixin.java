package com.sccadd.mixin;

import com.sccadd.iface.FilterWindow;
import de.scc.calculator.network.ItemFilter;
import de.scc.calculator.network.StorageConnectorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a filter hold more entries than the nine SCC gives it. Only the Storage Connector's is made big (see
 * {@code StorageConnectorBigFilterMixin}); every other filter stays at nine.
 */
@Mixin(value = ItemFilter.class, remap = false)
public abstract class ItemFilterMixin implements FilterWindow {
    @Shadow
    private String[] entries;

    private int sccadd$base;

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 9), require = 0)
    private int sccadd$sizeAtBirth(int original) {
        return FilterWindow.BIG.get() || sccadd$builtByConnector() ? FilterWindow.BUS_SIZE : original;
    }

    /** True when a Storage Connector is the one constructing this filter (its constructors cannot be hooked directly). */
    private static boolean sccadd$builtByConnector() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames.limit(6)
                .anyMatch(frame -> StorageConnectorBlockEntity.class.isAssignableFrom(frame.getDeclaringClass())));
    }

    @ModifyConstant(method = {"entry", "set", "firstFree", "compile", "load"}, constant = @Constant(intValue = 9), require = 0)
    private int sccadd$size(int original) {
        return this.entries == null ? original : this.entries.length;
    }

    /** What a screen calls slot n is slot base + n here (the client's page; always 0 on the server). */
    @ModifyVariable(method = {"entry", "set"}, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int sccadd$shift(int slot) {
        return slot < 0 ? slot : slot + this.sccadd$base;
    }

    /** The first free slot of the page being looked at, so "add pattern" lands where the player sees it. */
    @Inject(method = "firstFree", at = @At("RETURN"), cancellable = true, require = 0)
    private void sccadd$firstFreeOnPage(CallbackInfoReturnable<Integer> cir) {
        if (this.sccadd$base == 0 && this.entries != null && this.entries.length <= FilterWindow.SCC_PAGE) {
            return;
        }
        for (int i = 0; i < FilterWindow.SCC_PAGE; i++) {
            int absolute = this.sccadd$base + i;
            if (this.entries != null && absolute < this.entries.length && (this.entries[absolute] == null || this.entries[absolute].isEmpty())) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    @Override
    public int sccadd$capacity() {
        return this.entries == null ? FilterWindow.SCC_PAGE : this.entries.length;
    }

    @Override
    public int sccadd$base() {
        return this.sccadd$base;
    }

    @Override
    public void sccadd$setBase(int base) {
        this.sccadd$base = Math.max(0, base);
    }
}
