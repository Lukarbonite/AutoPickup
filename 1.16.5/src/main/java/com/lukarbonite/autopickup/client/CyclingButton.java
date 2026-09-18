package com.lukarbonite.autopickup.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

import java.util.Arrays;
import java.util.function.Function;

/**
 * Stand-in for {@code CycleButton}, which only arrived in 1.17.
 *
 * <p>Shows "Label: Value" using the same {@code options.generic_value} key vanilla uses, and advances
 * to the next value on each click.
 */
public class CyclingButton<T> extends Button {

    public interface Listener<T> {
        void onValueChange(CyclingButton<T> button, T value);
    }

    private static final Boolean[] ON_OFF = {Boolean.TRUE, Boolean.FALSE};

    private final Component label;
    private final T[] values;
    private final Function<T, Component> valueText;
    private final Listener<T> listener;
    private int index;

    public CyclingButton(int x, int y, int width, int height, Component label, T[] values, T initial,
                         Function<T, Component> valueText, Listener<T> listener) {
        super(x, y, width, height, label, button -> {});
        this.label = label;
        this.values = values;
        this.valueText = valueText;
        this.listener = listener;
        this.index = Math.max(0, Arrays.asList(values).indexOf(initial));
        updateMessage();
    }

    /** Equivalent of {@code CycleButton.onOffBuilder(initial)}. */
    public static CyclingButton<Boolean> onOff(int x, int y, int width, int height, Component label,
                                               boolean initial, Listener<Boolean> listener) {
        return new CyclingButton<>(x, y, width, height, label, ON_OFF, initial,
                v -> v ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF, listener);
    }

    @Override
    public void onPress() {
        index = (index + 1) % values.length;
        updateMessage();
        listener.onValueChange(this, values[index]);
    }

    private void updateMessage() {
        setMessage(new TranslatableComponent("options.generic_value", label, valueText.apply(values[index])));
    }
}
