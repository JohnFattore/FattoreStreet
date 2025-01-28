import pandas as pd
import os
import json
import torch

# Load your Yelp dataset (adjust file path as needed)
file_path = os.path.join(os.path.dirname(__file__), "yelp_dataset/reviews.pkl")
reviews = pd.read_pickle(file_path)
reviews = reviews[reviews["city"] == "Nashville"]
nashvilleReviews = reviews[reviews["categories"].str.contains("Restaurants", na=False)]

nashvilleReviews.to_pickle("reviewsNashville.pkl")