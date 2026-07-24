import { SortableTable } from "../SortableTable";
import { restaurantColumns } from "./restaurantColumns";
import { IRestaurant } from "../../interfaces";

interface RestaurantRecommendTableProps {
  restaurants: IRestaurant[];
  isLoading: boolean;
  errors: unknown[];
  setRestaurant: (r: IRestaurant) => void;
}

export default function RestaurantRecommendTable({
  restaurants,
  isLoading,
  errors,
  setRestaurant,
}: RestaurantRecommendTableProps) {
  const hasError = errors.some(Boolean);
  if (!isLoading && !hasError && restaurants.length == 0) {
    return <h3>No Data</h3>;
  }

  return (
    <>
      <h3>Recommended Restaurants</h3>
      <SortableTable
        data={restaurants}
        columns={restaurantColumns(setRestaurant)}
        isLoading={isLoading}
        errors={errors}
      />
    </>
  );
}
