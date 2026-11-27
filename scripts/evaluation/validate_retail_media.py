"""Check the downloaded files and their joins to the existing retail package."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

from eval_v2 import read_json, validate_package


def verify_file(root, record):
    path = (root / record["file"]).resolve()
    if not path.is_relative_to(root) or not path.is_file():
        raise ValueError(f"Missing or misplaced file: {record['file']}")
    if hashlib.sha256(path.read_bytes()).hexdigest() != record["sha256"]:
        raise ValueError(f"Checksum mismatch: {record['file']}")


def validate_media(directory):
    root = directory.resolve()
    media = read_json(root / "media.json")
    catalog_name = media["catalogPackage"]
    if not catalog_name or Path(catalog_name).name != catalog_name or catalog_name in {".", ".."}:
        raise ValueError("Expected a catalog directory name")
    catalog = root.parent / catalog_name
    validate_package(catalog)
    source = read_json(catalog / "manifest.json")
    docs = {doc["id"]: doc for doc in source["documents"]}
    skus = {p["sku"] for p in read_json(catalog / "catalog.json")["products"]}
    covered = set()
    missing = set()
    seen = set()
    for pic in media["images"]:
        verify_file(root, pic)
        if pic["id"] in seen:
            raise ValueError(f"Repeated image ID: {pic['id']}")
        seen.add(pic["id"])
        doc = docs[pic["documentId"]]
        if not pic["skus"] or not set(pic["skus"]) <= set(doc["skus"]):
            raise ValueError(f"Wrong SKU/document pairing: {pic['id']}")
        if pic.get("sectionId") and pic["sectionId"] not in {s["id"] for s in doc["sections"]}:
            raise ValueError(f"Unknown section: {pic['id']}")
        if "originalSection" in pic:
            anchor = pic["originalSection"]
            original = docs[anchor["documentId"]]
            original_sections = {s["id"] for s in original["sections"]}
            if not set(pic["skus"]) <= set(original["skus"]) or anchor["sectionId"] not in original_sections:
                raise ValueError(f"Wrong original manual section: {pic['id']}")
        if not all(pic[k] for k in ("author", "license", "captionZh", "view")) or min(pic["width"], pic["height"]) <= 0:
            raise ValueError(f"Incomplete image metadata: {pic['id']}")
        if not all(pic[k].startswith("https://") for k in ("sourceUrl", "licenseUrl", "downloadUrl")):
            raise ValueError(f"Incomplete source or license: {pic['id']}")
        covered.update(pic["skus"])
    for gap in media["missingImages"]:
        if not gap["reason"] or set(gap["skus"]) != set(docs[gap["documentId"]]["skus"]):
            raise ValueError("Invalid image coverage gap")
        missing.update(gap["skus"])
    for excluded in media.get("excludedImages", []):
        verify_file(root, excluded)
        if not excluded["reason"] or excluded["id"] in seen:
            raise ValueError("Excluded image also marked as usable or missing its reason")
    if covered & missing or covered | missing != skus:
        raise ValueError("Image coverage does not match the catalog")
    originals = {rec["file"]: rec for rec in media.get("originalFiles", [])}
    for original in originals.values():
        verify_file(root, original)
    manual_index = root / "manuals.json"
    chapters = 0
    if manual_index.is_file():
        for manual in read_json(manual_index)["manuals"]:
            if not manual["skus"] or not set(manual["skus"]) <= skus:
                raise ValueError(f"Unmatched manual: {manual['model']}")
            if "textArchive" in manual:
                archive = originals[manual["textArchive"]]
                with ZipFile(root / archive["file"]) as bundle:
                    for chapter in manual["chapters"]:
                        if hashlib.sha256(bundle.read(chapter["member"])).hexdigest() != chapter["sha256"]:
                            raise ValueError(f"Changed original chapter: {chapter['member']}")
                        chapters += 1
    return {"images": len(seen), "uniqueImages": len({i["sha256"] for i in media["images"]}),
            "coveredSkus": len(covered), "missingSkus": len(missing), "originalChapters": chapters,
            "excludedImages": len(media.get("excludedImages", []))}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    print(json.dumps(validate_media(parser.parse_args().directory), ensure_ascii=False))
