import yt_dlp
import json
import os

def get_stream_details(target_url):
    """
    Video metadata aur playable streams extract karta hai
    """
    ydl_opts = {
        'skip_download': True,
        'extract_flat': False,
        'no_warnings': True,
        'quiet': True,
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'ios'] # Bot blocks avoid karne ke liye
            }
        }
    }

    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        try:
            info = ydl.extract_info(target_url, download=False)
            if 'entries' in info and info['entries']:
                info = info['entries'][0]

            formats = []
            seen = set()

            for f in info.get("formats", []):
                h = f.get("height")
                # Sirf video streams
                if h and h not in seen and f.get("vcodec") != "none":
                    seen.add(h)
                    formats.append({
                        "quality": f"{h}p",
                        "format_id": str(f.get("format_id")),
                        "ext": "mp4",
                        "stream_url": f.get("url", "")
                    })

            # High to Low sort
            formats.sort(key=lambda x: int(x["quality"].replace("p", "")), reverse=True)

            return json.dumps({
                "success": True,
                "title": info.get("title"),
                "uploader": info.get("uploader") or info.get("channel"),
                "thumbnail": info.get("thumbnail"),
                "duration": info.get("duration_string", "00:00"),
                "formats": formats
            })
        except Exception as e:
            return json.dumps({"success": False, "error": str(e)})


def download_media_file(webpage_url, format_id, save_folder, file_title):
    """
    Selected format ko phone ke download directory me save karta hai
    """
    out_template = os.path.join(save_folder, f"{file_title}.%(ext)s")
    
    ydl_opts = {
        'format': f"{format_id}+bestaudio/best",
        'outtmpl': out_template,
        'overwrites': True,
        'no_warnings': True,
        'extractor_args': {
            'youtube': {'player_client': ['android', 'ios']}
        }
    }

    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        ydl.download([webpage_url])
