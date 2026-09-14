import yt_dlp
import json
import re

def clean_filename(title):
    return re.sub(r'[\\/*?:"<>|]', "", title)[:80]

def fetch_info(query_or_url):
    query = query_or_url.strip()

    # Search keyword vs direct link check
    if not (query.startswith("http://") or query.startswith("https://")):
        target = f"ytsearch1:{query}"
    else:
        target = query

    ydl_opts = {
        'skip_download': True,
        'extract_flat': False,
        'no_warnings': True,
        'quiet': True,
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'ios']  # Direct YouTube mobile clients
            }
        }
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(target, download=False)
            
            # Search result handling
            if 'entries' in info and info['entries']:
                info = info['entries'][0]

            formats_out = []
            seen = set()

            for f in info.get("formats", []):
                h = f.get("height")
                # Sirf original playable video streams
                if h and h not in seen and f.get("vcodec") != "none":
                    seen.add(h)
                    formats_out.append({
                        "label": f"{h}p",
                        "format_id": str(f.get("format_id")),
                        "ext": "mp4",
                        "direct_url": f.get("url", "")
                    })

            formats_out.sort(key=lambda x: int(x["label"].replace("p", "")), reverse=True)

            return json.dumps({
                "success": True,
                "title": info.get("title"),
                "uploader": info.get("uploader") or info.get("channel"),
                "thumbnail": info.get("thumbnail"),
                "duration": info.get("duration_string", "00:00"),
                "webpage_url": info.get("webpage_url", query),
                "formats": formats_out
            })

    except Exception as err:
        return json.dumps({"success": False, "error": str(err)})

def run_download(web_url, format_id, ext, out_dir, title):
    safe_name = clean_filename(title)
    out_path = f"{out_dir}/{safe_name}.%(ext)s"

    # Audio + Video auto-merge selection
    chosen_format = f"{format_id}+bestaudio/best" if ext == "mp4" else "bestaudio/best"

    ydl_opts = {
        'format': chosen_format,
        'outtmpl': out_path,
        'overwrites': True,
        'no_warnings': True,
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'ios']
            }
        }
    }

    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        ydl.download([web_url])
