package com.chimi.menu;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.Optional;

/** Native "choose a file" window on your PC. */
public final class FilePicker {
    private FilePicker() {}

    public static Optional<Path> pickImage() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(5);
            filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg"))
                    .put(stack.UTF8("*.gif")).put(stack.UTF8("*.bmp")).flip();
            String result = TinyFileDialogs.tinyfd_openFileDialog("Choose a background image", "", filters, "Images", false);
            return result == null ? Optional.empty() : Optional.of(Path.of(result));
        }
    }
}
