import React from "react";
import { Spinner } from "react-bootstrap";
import { useSelector } from "react-redux";
import { useGetCurrentUserQuery } from "../functions/api";
import { RootState } from "../main";
import ErrorPage from "./ErrorPage";
import LoginRequired from "./LoginRequired";

type AdminRouteProps = {
  children: React.ReactElement;
};

export function AdminRoute({ children }: AdminRouteProps) {
  const { access } = useSelector((state: RootState) => state.user);
  const {
    data: currentUser,
    isLoading,
    isError,
  } = useGetCurrentUserQuery(undefined, {
    skip: !access,
  });

  if (!access) {
    return (
      <LoginRequired
        title="Sign in required"
        message="Please sign in with an administrator account to access this page."
      />
    );
  }

  if (isLoading) {
    return <Spinner animation="border" />;
  }

  if (isError || !currentUser?.isStaff) {
    return <ErrorPage />;
  }

  return children;
}
