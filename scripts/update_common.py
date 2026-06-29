#!/usr/bin/env python3
import sys
import json


def main(path):
    print(f"Updateing the JDKs in {path}")
    with open(path) as in_file:
        mx_config = json.load(in_file)
        if path.endswith("common.json"):
            jdks = mx_config["jdks"]

            jdks["labsjdk-gdart"] = {"name": "labsjdk", "version": "ce-25+37-jvmci-b01", "platformspecific": True}
        elif path.endswith("binaries.json"):
            bins = mx_config["jdk-binaries"]
            latest = bins["labsjdk-ce-latest"]
            bins["labsjdk-gdart"] = latest
    with open(path, "w") as out_file:
        json.dump(mx_config, out_file)


if __name__ == "__main__":
    main(sys.argv[1])
