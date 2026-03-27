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

print(f"Using {device} device")

# Sample Dataset
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
batch_size = 128
num_epochs = 30
learning_rate = 0.001

# Prepare DataLoader
dataset = InteractionDataset(user_ids_mapped, restaurant_ids_mapped, ratings)
data_loader = DataLoader(dataset, batch_size=batch_size, shuffle=True)

# Initialize Model, Loss, and Optimizer
model = NeuralCF(num_users, num_restaurants, embedding_dim).to(device)
criterion = nn.MSELoss()  # Cost Function
optimizer = optim.Adam(model.parameters(), lr=learning_rate) # cost function optimizer, like Stochastic Gradient Descent


# Training Loop
for epoch in range(num_epochs):
    model.train()
    total_loss = 0
    for user_id, restaurant_id, rating in data_loader:
        optimizer.zero_grad()
        prediction = model(user_id, restaurant_id).squeeze()
        rating = rating.squeeze()
        loss = criterion(prediction, rating)
        loss.backward()
        optimizer.step()
        total_loss += loss.item()
    print(f"Epoch {epoch + 1}/{num_epochs}, Loss: {total_loss:.4f}, Time: {datetime.now()}")

torch.save(model.state_dict(), 'restaurantSuggester.pth')

model.eval()
test_user = torch.tensor([3]).to(device)  # Example user
test_restaurant = torch.tensor([restaurant_id_to_int["C9K3579SJgLPp0oAOM29wg"]]).to(device)  # Example restaurant
predicted_rating = model(test_user, test_restaurant).item()
print(f"Predicted rating for user 3 and Restaurant {restaurant_id_to_name['C9K3579SJgLPp0oAOM29wg']}: {predicted_rating:.2f}")
