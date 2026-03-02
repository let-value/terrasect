package terrasect.extender;

import org.jetbrains.annotations.Nullable;

public interface PresetIdHolder {
  @Nullable
  String terrasect$getPresetId();

  void terrasect$setPresetId(@Nullable String presetId);
}
