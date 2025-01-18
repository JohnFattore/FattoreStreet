import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, Dataset

# Sample Dataset
class InteractionDataset(Dataset):
    def __init__(self, user_ids, restaurant_ids, ratings):
        self.user_ids = torch.tensor(user_ids, dtype=torch.long)
        self.restaurant_ids = torch.tensor(restaurant_ids, dtype=torch.long)
        self.ratings = torch.tensor(ratings, dtype=torch.float32)

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
        self.activation = nn.ReLU()

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

# Example Data
user_ids = [0, 1, 2, 0, 1]
restaurant_ids = [0, 1, 2, 2, 0]
ratings = [5.0, 4.0, 3.0, 5.0, 4.5]  # Example ratings or interactions

# Hyperparameters
num_users = 3  # Number of unique users
num_restaurants = 3  # Number of unique restaurants
embedding_dim = 8
batch_size = 2
num_epochs = 10
learning_rate = 0.001

# Prepare DataLoader
dataset = InteractionDataset(user_ids, restaurant_ids, ratings)
data_loader = DataLoader(dataset, batch_size=batch_size, shuffle=True)

# Initialize Model, Loss, and Optimizer
model = NeuralCF(num_users, num_restaurants, embedding_dim)
criterion = nn.MSELoss()  # For regression (rating prediction)
optimizer = optim.Adam(model.parameters(), lr=learning_rate)

# Training Loop
for epoch in range(num_epochs):
    model.train()
    total_loss = 0
    for user_id, restaurant_id, rating in data_loader:
        optimizer.zero_grad()
        prediction = model(user_id, restaurant_id).squeeze()
        loss = criterion(prediction, rating)
        loss.backward()
        optimizer.step()
        total_loss += loss.item()
    print(f"Epoch {epoch + 1}/{num_epochs}, Loss: {total_loss:.4f}")

# Testing the Model
model.eval()
test_user = torch.tensor([0])  # Example user
test_restaurant = torch.tensor([1])  # Example restaurant
predicted_rating = model(test_user, test_restaurant).item()
print(f"Predicted rating for User 0 and Restaurant 1: {predicted_rating:.2f}")