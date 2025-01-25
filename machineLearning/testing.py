import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, Dataset
import os
import json
import pandas as pd
from datetime import datetime

device = (
    "cuda"
    if torch.cuda.is_available()
    else "mps"
    if torch.backends.mps.is_available()
    else "cpu"
)

class InteractionDataset(Dataset):
    def __init__(self, user_ids, restaurant_ids, ratings):
        self.user_ids = torch.tensor(user_ids, dtype=torch.long).to(device)
        self.restaurant_ids = torch.tensor(restaurant_ids, dtype=torch.long).to(device)
        self.ratings = torch.tensor(ratings, dtype=torch.float32).to(device)

    def __len__(self):
        return len(self.user_ids)

    def __getitem__(self, idx):
        return self.user_ids[idx], self.restaurant_ids[idx], self.ratings[idx]

# Neural Collaborative Filtering Model
class NeuralCF(nn.Module):
    def __init__(self, num_users, num_restaurants, embedding_dim):
        super(NeuralCF, self).__init__()
        # Embedding layers for users and restaurants
        self.user_embedding = nn.Embedding(num_users, embedding_dim)
        self.restaurant_embedding = nn.Embedding(num_restaurants, embedding_dim)

        # Feedforward layers
        self.fc1 = nn.Linear(embedding_dim * 2, 64)
        self.fc2 = nn.Linear(64, 32)
        self.fc3 = nn.Linear(32, 1)
        self.activation = nn.ReLU() # activation function, like sigmoid

    def forward(self, user_id, restaurant_id):
        # Get embeddings
        user_emb = self.user_embedding(user_id)
        restaurant_emb = self.restaurant_embedding(restaurant_id)

        # Concatenate embeddings
        x = torch.cat([user_emb, restaurant_emb], dim=-1)

        # Pass through feedforward network
        x = self.activation(self.fc1(x))
        x = self.activation(self.fc2(x))
        x = self.fc3(x)
        return x

print(f"Using {device} device")

# Here to hyperparameters is copy of training/testing
file_path = os.path.join(os.path.dirname(__file__), "yelp_dataset/reviews.pkl")
reviews = pd.read_pickle(file_path)
reviews = reviews[reviews["city"] == "Nashville"]
nashvilleReviews = reviews[reviews["categories"].str.contains("Restaurants", na=False)]

user_ids = nashvilleReviews["user_id"].tolist()
restaurant_ids = nashvilleReviews["restaurant_id"].tolist()
ratings = nashvilleReviews["rating"].tolist()
restaurant_names = nashvilleReviews["name"].tolist()  # Replace "name" with the actual column name for restaurant names
restaurant_id_to_name = {rid: name for rid, name in zip(restaurant_ids, restaurant_names)}

#dictionaries
user_id_to_int = {uid: idx for idx, uid in enumerate(set(user_ids))}
restaurant_id_to_int = {rid: idx for idx, rid in enumerate(set(restaurant_ids))}
int_to_user_id = {value: key for key, value in user_id_to_int.items()}
int_to_restaurant_id = {value: key for key, value in restaurant_id_to_int.items()}

# lists
user_ids_mapped = [user_id_to_int[uid] for uid in user_ids]
restaurant_ids_mapped = [restaurant_id_to_int[rid] for rid in restaurant_ids]

# Hyperparameters
num_users = len(set(user_ids_mapped))  # Number of unique users
num_restaurants = len(set(restaurant_ids_mapped))  # Number of unique restaurants
embedding_dim = 8
learning_rate = 0.01

model = NeuralCF(num_users, num_restaurants, embedding_dim).to(device)
criterion = nn.MSELoss()  # Cost Function
optimizer = optim.Adam(model.parameters(), lr=learning_rate) # cost function optimizer, like Stochastic Gradient Descent

model = NeuralCF(num_users, num_restaurants, embedding_dim).to(device)

model.load_state_dict(torch.load('restaurantSuggester.pth', weights_only=True))

model.train()
                    # hattie Bs, peg leg porker, pancake pantry, Acme, maiz de la vida
maxwellReviews = [[99999, restaurant_id_to_int["GXFMD0Z4jEVZBCsbPf4CTQ"], 4],
                  [99999, restaurant_id_to_int["C9K3579SJgLPp0oAOM29wg"], 1],
                  [99999, restaurant_id_to_int["pSmOH4a3HNNpYM82J5ycLA"], 2],
                  [99999, restaurant_id_to_int["GST3wg-wej15vHeCvaXE6w"], 4],
                  [99999, restaurant_id_to_int["2ntwsTW112z4csqvmM6IEA"], 5]]


new_user_ratings = torch.tensor(maxwellReviews).to(device)

# Fine-tune user embedding for the new user
total_loss = 0
for epoch in range(50):
    optimizer.zero_grad()
    # Get user, item, and rating tensors
    users = new_user_ratings[:, 0]
    items = new_user_ratings[:, 1]
    ratings = new_user_ratings[:, 2].float()

    # Predict and compute loss
    predictions = model(users, items).squeeze()
    loss = nn.MSELoss()(predictions, ratings)

    loss.backward()
    optimizer.step()
    total_loss += loss.item()
    print(f"Epoch {epoch + 1}/{50}, Loss: {loss.item():.4f}")

print("New user embeddings updated!")


model.eval()
test_user = torch.tensor([99999]).to(device)  # Example user
test_restaurant = torch.tensor([restaurant_id_to_int["C9K3579SJgLPp0oAOM29wg"]]).to(device)  # Example restaurant
predicted_rating = model(test_user, test_restaurant).item()
print(f"Predicted rating for maxwell and Restaurant {restaurant_id_to_name['C9K3579SJgLPp0oAOM29wg']}: {predicted_rating:.2f}")
