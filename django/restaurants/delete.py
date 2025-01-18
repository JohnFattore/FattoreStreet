import json
import os

file_path = os.path.join(os.path.dirname(__file__), "yelp_dataset/yelp_academic_dataset_business.json")

with open(file_path, "r") as file:
    for idx, line in enumerate(file):
        line = line.strip()
        if line:
            data = json.loads(line)
            print(data)

        if idx > 2:
            break