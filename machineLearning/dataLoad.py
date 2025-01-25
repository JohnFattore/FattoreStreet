import pandas as pd
import os
import json
import torch

# Load your Yelp dataset (adjust file path as needed)
file_path = os.path.join(os.path.dirname(__file__), "yelp_dataset/yelp_academic_dataset_review.json")

user_ids = []
restaurant_ids = []
ratings = []

with open(file_path, "r") as file:
    for idx, line in enumerate(file):
        line = line.strip()
        if line:
            data = json.loads(line)
            user_ids.append(data["user_id"])
            restaurant_ids.append(data["business_id"])
            ratings.append(data["stars"])

reviews = pd.DataFrame({
    "user_id": user_ids,
    "restaurant_id": restaurant_ids,
    "rating": ratings
})

file_path = os.path.join(os.path.dirname(__file__), "yelp_dataset/yelp_academic_dataset_business.json")

restaurant_ids = []
cities = []
states = []
names = []
categories = []

with open(file_path, "r") as file:
    for idx, line in enumerate(file):
        line = line.strip()
        if line:
            data = json.loads(line)
            restaurant_ids.append(data["business_id"])
            names.append(data["name"])
            cities.append(data["city"])
            states.append(data["state"])
            categories.append(data["categories"])

businesses = pd.DataFrame({
    "restaurant_id": restaurant_ids,
    "name": names,
    "state": states,
    "city": cities,
    "categories": categories
})

reviewsBusiness = pd.merge(reviews, businesses, on="restaurant_id", how="inner")

print(reviewsBusiness)

reviewsBusiness.to_pickle("reviews.pkl")