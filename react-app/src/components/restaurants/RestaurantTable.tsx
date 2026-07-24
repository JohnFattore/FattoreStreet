import { useSelector } from "react-redux";
import { RootState } from "../../main";
import RestaurantSearchBar from "./RestaurantSearchBar";
import { useState } from "react";
import { SortableTable } from "../SortableTable";
import { restaurantColumns } from "./restaurantColumns";
import { useGetRestaurantsQuery } from "../../functions/api";
import { IRestaurant } from "../../interfaces";

export default function RestaurantTable({
  setRestaurant,
}: {
  setRestaurant: (r: IRestaurant) => void;
}) {
  const location = useSelector((state: RootState) => state.location);
  const {
    data: restaurants = [],
    isLoading,
    error,
  } = useGetRestaurantsQuery({
    state: location.state,
    city: location.city,
  });
  const [search, setSearch] = useState("");

  const filteredRestaurants = restaurants.filter((restaurant) =>
    restaurant.name.toLowerCase().includes(search.toLowerCase()),
  );

  return (
    <>
      <RestaurantSearchBar setSearch={setSearch} />
      {!isLoading && !error && filteredRestaurants.length == 0 ? (
        <h3 role="noModels">No Data</h3>
      ) : (
        <SortableTable
          data={filteredRestaurants}
          columns={restaurantColumns(setRestaurant)}
          isLoading={isLoading}
          errors={[error]}
        />
      )}
    </>
  );
}
