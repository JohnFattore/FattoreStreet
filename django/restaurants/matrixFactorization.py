import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, Dataset
import os
import pandas as pd
from .models import Restaurant, Review

device = "cpu"

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
    
class MatrixFactorization(nn.Module):
    def __init__(self, num_users, num_items, num_features):
        super().__init__()                                          # following two matrix multiplied together should equal matrix M size m x n
        self.user_embedding = nn.Embedding(num_users, num_features) # Matrix "A" size m x r
        self.item_embedding = nn.Embedding(num_items, num_features) # Matrix "B" size n x r

    def forward(self, user_ids, item_ids):
        user_factors = self.user_embedding(user_ids)
        item_factors = self.item_embedding(item_ids)
        return (user_factors * item_factors).sum(dim=1)  # Dot product of embeddings

# Here to hyperparameters is copy of training/testing
def getRestaurantRecommendations(user):
    device = "cpu"
    file_path = os.path.join(os.path.dirname(__file__), "yelp_dataset/reviewsNashville.pkl")
    nashvilleReviews = pd.read_pickle(file_path)

    user_ids = nashvilleReviews["user_id"].tolist()
    restaurant_ids = nashvilleReviews["restaurant_id"].tolist()

    #dictionaries
    # important to sort list of unique user_ids/restaurant_ids before creating dictionary
    user_id_to_int = {uid: idx for idx, uid in enumerate(sorted(set(user_ids)))}
    restaurant_id_to_int = {rid: idx for idx, rid in enumerate(sorted(set(restaurant_ids)))}
    int_to_restaurant_id = {value: key for key, value in restaurant_id_to_int.items()}

    # lists
    user_ids_mapped = [user_id_to_int[uid] for uid in user_ids]
    restaurant_ids_mapped = [restaurant_id_to_int[rid] for rid in restaurant_ids]

    # Hyperparameters
    num_users = len(set(user_ids_mapped))  # Number of unique users
    num_restaurants = len(set(restaurant_ids_mapped))  # Number of unique restaurants
    num_epochs = 30
    learning_rate = 0.01
    num_features = 10 # r, which should be smaller than m and n

    # Model, loss, optimizer
    model = MatrixFactorization(num_users, num_restaurants, num_features).to(device)

    # model.load_state_dict(torch.load('restaurants/matrixFactorModel.pth', weights_only=True))
    model.load_state_dict(torch.load('restaurants/matrixFactorModel.pth', map_location=torch.device('cpu')))

    new_user_id = "maxwell"  # Example new user ID
    new_user_int = len(user_id_to_int)  # Assign the next available integer
    user_id_to_int[new_user_id] = new_user_int

    # adding new user to embeddings
    old_weights = model.user_embedding.weight.data  # Existing weights
    new_user_embedding = nn.Embedding(num_users + 1, num_features).to(device)
    new_user_embedding.weight.data[:num_users] = old_weights
    nn.init.normal_(new_user_embedding.weight.data[num_users:], mean=0.0, std=0.01)  # Random initialization
    model.user_embedding = new_user_embedding

    # Example: New user data
    new_user_id = new_user_int  # The integer ID returned when adding the new use

    new_user_ratings = []
    new_restaurant_ids = []

    for review in Review.objects.filter(user=user.id):
        new_user_ratings.append(review.rating)
        new_restaurant_ids.append(restaurant_id_to_int[review.restaurant.yelp_id])

    new_user_tensor = torch.tensor([new_user_id] * len(new_restaurant_ids)).to(device)  # Repeat the new user's ID
    new_restaurant_tensor = torch.tensor(new_restaurant_ids, dtype=torch.int64).to(device)
    new_ratings_tensor = torch.tensor(new_user_ratings, dtype=torch.float32).to(device)

    # Set the model to training mode
    model.train()

    # Optimizer and loss function
    optimizer = optim.Adam(model.parameters(), lr=learning_rate)
    loss_fn = nn.MSELoss()

    for epoch in range(num_epochs):
        optimizer.zero_grad()
        predictions = model(new_user_tensor, new_restaurant_tensor)
        loss = loss_fn(predictions, new_ratings_tensor)
        loss.backward()
        optimizer.step()

    model.eval()
    new_user_predictions = {}

    test_user = torch.tensor([user_id_to_int["maxwell"]]).to(device)  # Example user
    for key in int_to_restaurant_id:
        test_restaurant = torch.tensor([key]).to(device)
        predicted_rating = model(test_user, test_restaurant).item()
        new_user_predictions[key] = predicted_rating

    new_user_predictions = {
            key: value for key, value in new_user_predictions.items() if key not in new_restaurant_ids
    }

    top_5_items = sorted(new_user_predictions.items(), key=lambda x: x[1], reverse=True)[:5]

    recommendedRestaurants = []

    for key, value in top_5_items:
        recommendedRestaurants.append(int_to_restaurant_id[key])

    return recommendedRestaurants