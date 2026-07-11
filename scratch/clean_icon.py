import os
import sys

def main():
    # Automatically check and install Pillow if not installed
    try:
        from PIL import Image, ImageOps
    except ImportError:
        print("Pillow library not found. Installing...")
        import subprocess
        subprocess.check_call([sys.executable, "-m", "pip", "install", "pillow"])
        from PIL import Image, ImageOps

    input_path = r"C:\Users\hwanyu365\.gemini\antigravity-cli\brain\f409b679-6c98-4773-ad9c-2608ab6ca1c3\ic_launcher_single_neon_line_1783771382302.jpg"
    output_path = r"E:\01_dev\page-tune\app\src\main\res\drawable\ic_launcher.png"

    if not os.path.exists(input_path):
        print(f"Input file not found: {input_path}")
        return

    print("Opening source icon image...")
    img = Image.open(input_path).convert("RGBA")
    width, height = img.size

    # We want to crop out the outer white borders.
    # Usually, the image generator creates a white background at the very edge (often around 10-20 pixels).
    # Let's inspect the pixels and find where the white border ends, or simply crop by a safe margin (e.g., 5%),
    # and then place it onto a clean solid #171717 background.
    
    # 1. Define safe crop margins (5% from each side) to guarantee all white borders are discarded
    margin_w = int(width * 0.05)
    margin_h = int(height * 0.05)
    
    cropped_img = img.crop((margin_w, margin_h, width - margin_w, height - margin_h))
    
    # 2. Create a brand new clean solid background of color #171717 (23, 23, 23)
    target_bg_color = (23, 23, 23, 255) # #171717 in RGBA
    new_img = Image.new("RGBA", (width, height), target_bg_color)
    
    # 3. Resize cropped logo back to original size or paste it centrally
    # Let's resize cropped_img back to fit nicely inside the clean #171717 canvas with a tiny padding
    target_size = (int(width * 0.9), int(height * 0.9))
    resized_logo = cropped_img.resize(target_size, Image.Resampling.LANCZOS)
    
    # Paste centered
    offset_x = (width - target_size[0]) // 2
    offset_y = (height - target_size[1]) // 2
    new_img.paste(resized_logo, (offset_x, offset_y), resized_logo)
    
    # Save as PNG
    print(f"Saving cleaned icon to: {output_path}")
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    new_img.save(output_path, "PNG")
    print("Icon cleaning completed successfully!")

if __name__ == "__main__":
    main()
