import RestaurantTable from "../components/restaurants/RestaurantTable";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import ReviewForm from "../components/restaurants/ReviewForm";
import { useState } from "react";
import { IRestaurant } from "../interfaces";
import { Button } from "react-bootstrap";
import LoginRequired from "../components/LoginRequired";
import ReviewTable from "../components/restaurants/ReviewTable";
import ReviewMap from "../components/restaurants/ReviewMap";
//import RestaurantRecommend from '../components/restaurants/RestaurantRecommend'

export default function Restaurants() {
  const { access } = useSelector((state: RootState) => state.user);

  const [restaurant, setRestaurant] = useState<IRestaurant>({
    yelp_id: "",
    name: "",
    address: "",
    state: "",
    city: "",
    latitude: 1,
    longitude: 1,
    categories: "",
    stars: "",
    review_count: 0,
    id: 0,
  });

  const [showReviewModal, setShowReviewModal] = useState(false);
  const [showMap, setShowMap] = useState(false);

  // Function to handle opening the review modal
  const handleOpenReviewModal = (selectedRestaurant: IRestaurant) => {
    setRestaurant(selectedRestaurant);
    setShowReviewModal(true);
  };
  if (!access) {
    return (
      <div className="restaurants-page">
        <LoginRequired
          title="Nashville Restaurant Explorer"
          message="Join our community to see personal reviews and get AI-powered recommendations."
          buttonText="Sign In to Unlock Full Access"
        />
        <RestaurantTable setRestaurant={handleOpenReviewModal} />
        <p>Data provided by Yelp</p>
      </div>
    );
  }

  return (
    <div className="restaurants-page">
      <h1>Nashville Restaurants</h1>
      <ReviewTable />
      <Button onClick={() => setShowMap((prev) => !prev)}>
        {" "}
        {showMap ? "Hide Map" : "Show Map"}{" "}
      </Button>
      {showMap && <ReviewMap />}
      {/*<RestaurantRecommend setRestaurant={handleOpenReviewModal} />*/}
      <ReviewForm
        restaurant={restaurant}
        show={showReviewModal}
        onHide={() => setShowReviewModal(false)}
      />
      <RestaurantTable setRestaurant={handleOpenReviewModal} />
      <p>Data provided by Yelp</p>
    </div>
  );
}
