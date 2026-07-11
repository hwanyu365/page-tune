import os
import sys

def main():
    try:
        from PIL import Image
    except ImportError:
        import subprocess
        subprocess.check_call([sys.executable, "-m", "pip", "install", "pillow"])
        from PIL import Image

    input_path = r"C:\Users\hwanyu365\.gemini\antigravity-cli\brain\f409b679-6c98-4773-ad9c-2608ab6ca1c3\ic_launcher_single_neon_line_1783771382302.jpg"
    output_path = r"E:\01_dev\page-tune\app\src\main\res\drawable\ic_launcher.png"

    if not os.path.exists(input_path):
        print(f"Input file not found: {input_path}")
        return

    print("Opening source icon image for high-precision cleaning...")
    img = Image.open(input_path).convert("RGBA")
    width, height = img.size
    pixels = img.load()

    # Define target background color (#171717)
    target_bg_color = (23, 23, 23, 255) # RGB 23, 23, 23

    # We will process pixels.
    # 1. Any pixel that is bright/whitish (R > 180, G > 180, B > 180) -> change to #171717
    # 2. To ensure no remnants in the outer corners, any pixel within the outer 8% margin
    #    that is not part of the glowing orange logo (which is highly red-saturated) -> change to #171717
    # Orange logo pixels usually have high Red value and lower Green/Blue, e.g., R > 150, G < 120
    
    margin_w = int(width * 0.08)
    margin_h = int(height * 0.08)

    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            
            # Condition A: Bright/white pixel (e.g. background glare, corners)
            is_bright = (r > 160 and g > 160 and b > 160)
            
            # Condition B: Edge pixel that isn't part of the orange neon logo
            is_in_edge_margin = (x < margin_w or x > width - margin_w or y < margin_h or y > height - margin_h)
            is_orange = (r > 120 and g < 130 and b < 100) # Orange detection heuristic
            
            if is_bright or (is_in_edge_margin and not is_orange):
                pixels[x, y] = target_bg_color

    # Save as PNG
    print(f"Saving high-precision cleaned icon to: {output_path}")
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    img.save(output_path, "PNG")
    print("Precision icon cleaning completed successfully!")

if __name__ == "__main__":
    main()
