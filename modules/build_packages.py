"""Build the non-installable module assets used by the future 3.0.0 host."""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SOURCE = ROOT.parent
OUT = ROOT / "packages" / "3.0.0"
HOST_VERSION = "3.0.0"
SOURCE_SNAPSHOT = "2.1.5"
APP_ID = "org.simpleweather.app"


MODULES = [
    {
        "id": "weather",
        "title": {"zh-CN": "天气", "en": "Weather"},
        "entry": "host://weather",
        "groups": ["weather"],
        "keys": {"weather": ["selected", "saved"]},
        "permissions": ["android.permission.INTERNET", "android.permission.ACCESS_COARSE_LOCATION"],
    },
    {
        "id": "weather-map",
        "title": {"zh-CN": "天气地图", "en": "Weather map"},
        "entry": "host://weather-map",
        "groups": ["toolbox"],
        "keys": {"toolbox": ["radar_region", "map_product"]},
        "permissions": ["android.permission.INTERNET"],
    },
    {
        "id": "status",
        "title": {"zh-CN": "状态", "en": "Status"},
        "entry": "host://status",
        "groups": ["status_places", "status_routes"],
        "keys": {"status_places": ["places", "selected"], "status_routes": ["routes"]},
        "permissions": [
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.CAMERA",
        ],
    },
    {
        "id": "schedule",
        "title": {"zh-CN": "课程表", "en": "Schedule"},
        "entry": "host://schedule",
        "groups": ["timetable"],
        "keys": {"timetable": ["data"]},
        "permissions": [
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.RECEIVE_BOOT_COMPLETED",
        ],
    },
    {
        "id": "astronomy",
        "title": {"zh-CN": "天文", "en": "Astronomy"},
        "entry": "host://astronomy",
        "groups": ["toolbox"],
        "keys": {"toolbox": ["astro_*", "global_time_mode"]},
        "permissions": [],
    },
    {
        "id": "earthquake",
        "title": {"zh-CN": "地震", "en": "Earthquake"},
        "entry": "host://earthquake",
        "groups": ["toolbox"],
        "keys": {"toolbox": ["quake_*"]},
        "permissions": ["android.permission.INTERNET", "android.permission.ACCESS_COARSE_LOCATION"],
    },
    {
        "id": "random",
        "title": {"zh-CN": "随机抽取", "en": "Random draw"},
        "entry": "host://random",
        "groups": ["random_choice"],
        "keys": {"random_choice": ["data"]},
        "permissions": [],
    },
    {
        "id": "todo",
        "title": {"zh-CN": "待办事项", "en": "To-do"},
        "entry": "host://todo",
        "groups": ["todos"],
        "keys": {"todos": ["data"]},
        "permissions": [
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.RECEIVE_BOOT_COMPLETED",
        ],
    },
    {
        "id": "global",
        "title": {"zh-CN": "全局设置", "en": "Global settings"},
        "entry": "host://global",
        "groups": ["toolbox"],
        "keys": {"toolbox": ["host-wide settings"]},
        "permissions": [],
    },
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def commit() -> str:
    try:
        return subprocess.check_output(
            ["git", "-C", str(SOURCE), "rev-parse", "HEAD"], text=True
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unavailable"


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def module_manifest(module: dict, source_commit: str) -> dict:
    return {
        "format": "daidai-toolbox-module-package",
        "schemaVersion": 1,
        "moduleId": module["id"],
        "title": module["title"],
        "entry": module["entry"],
        "packageType": "host-extension-asset",
        "standaloneInstallable": False,
        "requiresHost": {"applicationId": APP_ID, "api": f"daidai-toolbox/{HOST_VERSION}"},
        "sourceSnapshot": SOURCE_SNAPSHOT,
        "sourceCommit": source_commit,
        "configurationGroups": module["groups"],
        "hostPermissions": module["permissions"],
        "license": "MIT for application code; see host NOTICE.md and docs/DATA-LICENSE.md for third-party data and services",
    }


def package_readme(module: dict) -> str:
    zh = module["title"]["zh-CN"]
    en = module["title"]["en"]
    groups = "、".join(module["groups"])
    return f"""# {zh} / {en} 模块资产包

- 模块 ID：`{module['id']}`
- 面向宿主：呆呆工具箱 3.0.0（`{APP_ID}`）
- 配置组：`{groups}`
- 源码快照：正式工具箱 2.1.5

这是面向未来 3.0.0 模块宿主的清单与配置契约包。当前正式版仍是单体 APK，
本包不能在 2.1.5 中直接安装、卸载或单独运行，也不包含把完整 APK 复制出来的伪模块。
3.0.0 宿主应先校验 `module.json`、配置契约、版本和权限，再决定是否安装和启用模块。

配置导入导出仍由宿主负责。导入不会恢复权限、缓存、接口限流、最近定位或正在运行的任务。
精确坐标、路线和其他个人资料只在用户主动导出时写入配置文件，不上传到本包构建过程。

第三方数据与网页服务的许可不由本包扩大，使用时仍须遵守宿主 `NOTICE.md` 和 `docs/DATA-LICENSE.md`。
"""


def build_one(module: dict, source_commit: str) -> dict:
    directory = OUT / module["id"]
    directory.mkdir(parents=True, exist_ok=True)
    manifest = module_manifest(module, source_commit)
    contract = {
        "format": "daidai-toolbox-module-configuration-contract",
        "schemaVersion": 1,
        "module": module["id"],
        "hostApi": f"daidai-toolbox/{HOST_VERSION}",
        "groups": [
            {"name": group, "keys": module["keys"].get(group, [])}
            for group in module["groups"]
        ],
        "validation": "The 3.0.0 host validates value types, ranges, nesting and model data before import.",
    }
    write_json(directory / "module.json", manifest)
    write_json(directory / "configuration-contract.json", contract)
    (directory / "README.md").write_text(package_readme(module), encoding="utf-8")
    (directory / "LICENSE").write_text((SOURCE / "LICENSE").read_text(encoding="utf-8"), encoding="utf-8")
    package = OUT / f"{module['id']}.zip"
    with zipfile.ZipFile(package, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name in ("module.json", "configuration-contract.json", "README.md", "LICENSE"):
            archive.write(directory / name, f"{module['id']}/{name}")
    return {
        "id": module["id"],
        "title": module["title"],
        "file": package.name,
        "sha256": sha256(package),
        "bytes": package.stat().st_size,
        "standaloneInstallable": False,
        "requiresHost": f"daidai-toolbox/{HOST_VERSION}",
    }


def main() -> None:
    source_commit = commit()
    OUT.mkdir(parents=True, exist_ok=True)
    records = [build_one(module, source_commit) for module in MODULES]
    index = {
        "format": "daidai-toolbox-module-index",
        "schemaVersion": 1,
        "hostApi": f"daidai-toolbox/{HOST_VERSION}",
        "sourceSnapshot": SOURCE_SNAPSHOT,
        "sourceCommit": source_commit,
        "packageType": "host-extension-asset",
        "standaloneInstallable": False,
        "packages": records,
    }
    write_json(OUT / "index.json", index)
    (OUT / "README.md").write_text(
        "# 呆呆工具箱 3.0.0 模块资产包\n\n"
        "本目录包含已实装栏目面向未来 3.0.0 宿主的模块资产包。每个 ZIP 都包含模块清单、配置契约、"
        "宿主版本要求、权限说明和许可文件。当前 2.1.5 仍为单体 APK，这些包不能独立安装、卸载或运行；"
        "3.0.0 宿主负责下载、校验、安装、启用和卸载。\n\n"
        "已包含：天气、天气地图、状态、课程表、天文、地震、随机抽取、待办事项及全局设置。\n",
        encoding="utf-8",
    )
    files = [OUT / "index.json", OUT / "README.md"]
    for module in MODULES:
        files.extend(OUT / module["id"] / name for name in ("module.json", "configuration-contract.json", "README.md", "LICENSE"))
        files.append(OUT / f"{module['id']}.zip")
    (OUT / "SHA256SUMS.txt").write_text(
        "".join(f"{sha256(path)}  {path.relative_to(OUT).as_posix()}\n" for path in files),
        encoding="utf-8",
    )
    print(json.dumps(index, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
