import { render, screen } from "@testing-library/react";
import { expect, test } from "vitest";
import Home from "./page";

test("랜딩 페이지는 pipibooks 제목을 보여 준다", () => {
  render(<Home />);
  expect(
    screen.getByRole("heading", { level: 1, name: "pipibooks" }),
  ).toBeDefined();
});
