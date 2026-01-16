package com.terrasect.common.testing;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.resolver.ClasspathResolver;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public final class SnapshotHtmlReports {
  private static final DefaultMustacheFactory MUSTACHE_FACTORY =
      new DefaultMustacheFactory(new ClasspathResolver("templates"));

  public static void writeIndex(File outDir, String title, List<ImageEntry> images)
      throws IOException {
    var html = renderTemplate("gallery/simple.mustache", new GalleryPage(title, images));
    Files.writeString(outDir.toPath().resolve("index.html"), html, StandardCharsets.UTF_8);
  }

  public static void writeIndexWithLegend(
      File outDir, String title, List<ImageEntry> images, List<LegendEntry> legend)
      throws IOException {
    var hasLegend = legend != null && !legend.isEmpty();
    var html =
        renderTemplate(
            "gallery/with-legend.mustache",
            new LegendGalleryPage(title, images, legend, hasLegend));
    Files.writeString(outDir.toPath().resolve("index.html"), html, StandardCharsets.UTF_8);
  }

  private static String renderTemplate(String name, Object context) throws IOException {
    var mustache = MUSTACHE_FACTORY.compile(name);
    var out = new StringWriter(4 * 1024);
    mustache.execute(out, context);
    return out.toString();
  }

  public record ImageEntry(String label, String file, Integer width, Integer height) {
    public static ImageEntry of(String label, String file) {
      return new ImageEntry(label, file, null, null);
    }

    public static ImageEntry of(String label, String file, int width, int height) {
      return new ImageEntry(label, file, width, height);
    }
  }

  private record GalleryPage(String title, List<ImageEntry> images) {}

  public record LegendEntry(String label, String colorHex) {}

  private record LegendGalleryPage(
      String title, List<ImageEntry> images, List<LegendEntry> legend, boolean hasLegend) {}

  private SnapshotHtmlReports() {}
}
