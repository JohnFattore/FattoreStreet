import pandas as pd
import os
import json
import torch

# Load your Yelp dataset (adjust file path as needed)
file_path = os.path.join(os.path.dirname(__file__), "yelp_dataset/yelp_academic_dataset_review.json")

data_list = []

with open(file_path, "r") as file:
    for idx, line in enumerate(file):
        line = line.strip()
        if line:
            data = json.loads(line)
            user_id = data["user_id"]
            business_id = data["business_id"]
            stars = data["stars"]
            data_list.append([user_id, business_id, stars])
        if idx > 2:
            break

df = pd.DataFrame(data_list, columns=["user_id", "business_id", "stars"])

interaction_matrix = df.pivot(index='user_id', columns='business_id', values='stars')
interaction_matrix = interaction_matrix.fillna(0)


user_ids = interaction_matrix.index.values
restaurant_ids = interaction_matrix.columns.values
ratings = interaction_matrix.values

# Prepare a list of (user_id, restaurant_id, rating) triples
user_restaurant_pairs = []
ratings_list = []

for i in range(len(user_ids)):
    for j in range(len(restaurant_ids)):
        if ratings[i][j] != 0:  # Only include non-zero ratings (interactions)
            user_restaurant_pairs.append([user_ids[i], restaurant_ids[j]])
            ratings_list.append(ratings[i][j])

# Convert to PyTorch tensors
user_restaurant_tensor = torch.tensor(user_restaurant_pairs, dtype=torch.long)
ratings_tensor = torch.tensor(ratings_list, dtype=torch.float32)

print("\nUser-Restaurant Pairs Tensor:")
print(user_restaurant_tensor[:5])  # Show first 5 pairs
print("\nRatings Tensor:")
print(ratings_tensor[:5])  # Show first 5 ratings