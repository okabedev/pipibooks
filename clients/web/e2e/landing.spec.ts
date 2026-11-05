import { expect, test } from "@playwright/test";

test("랜딩 페이지는 pipibooks 제목과 한국어 문서 언어를 가진다", async ({
  page,
}) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { level: 1, name: "pipibooks" }),
  ).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("lang", "ko");
});

test("헬스 엔드포인트는 UP을 반환한다", async ({ request }) => {
  const res = await request.get("/healthz");
  expect(res.status()).toBe(200);
  expect(await res.json()).toEqual({ status: "UP" });
});
