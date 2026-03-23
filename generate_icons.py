import os
from PIL import Image, ImageDraw

def generate_triangle_icon(size, output_path, is_round=False):
    # Create image with black background
    img = Image.new('RGBA', (size, size), (0, 0, 0, 255))
    draw = ImageDraw.Draw(img)

    # Path: M25,15 L25,93 L90,54 Z (Viewport: 108x108)
    # Scale points to target size
    scale = size / 108.0
    p1 = (25 * scale, 15 * scale)
    p2 = (25 * scale, 93 * scale)
    p3 = (90 * scale, 54 * scale)

    # Draw cyan triangle
    cyan = (0, 229, 255, 255) # #00E5FF
    draw.polygon([p1, p2, p3], fill=cyan)

    if is_round:
        # Create a circular mask
        mask = Image.new('L', (size, size), 0)
        mask_draw = ImageDraw.Draw(mask)
        mask_draw.ellipse((0, 0, size - 1, size - 1), fill=255)

        # Apply mask
        output = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        output.paste(img, (0, 0), mask=mask)
        img = output

    # Ensure directory exists
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    img.save(output_path)
    print(f"Generated {output_path}")

def main():
    densities = {
        "mdpi": (48, 108),
        "hdpi": (72, 162),
        "xhdpi": (96, 216),
        "xxhdpi": (144, 324),
        "xxxhdpi": (192, 432)
    }

    base_res_path = "app/src/main/res"

    for density, (launcher_size, foreground_size) in densities.items():
        # ic_launcher.png
        generate_triangle_icon(
            launcher_size,
            os.path.join(base_res_path, f"mipmap-{density}", "ic_launcher.png")
        )
        # ic_launcher_round.png
        generate_triangle_icon(
            launcher_size,
            os.path.join(base_res_path, f"mipmap-{density}", "ic_launcher_round.png"),
            is_round=True
        )
        # ic_launcher_foreground.png
        generate_triangle_icon(
            foreground_size,
            os.path.join(base_res_path, f"mipmap-{density}", "ic_launcher_foreground.png")
        )

if __name__ == "__main__":
    main()
