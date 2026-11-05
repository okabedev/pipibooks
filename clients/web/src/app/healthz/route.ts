import { connection } from "next/server";

// 컨테이너 헬스체크용. connection()으로 요청 시점에 실행되게 한다(Cache Components에서 프리렌더 방지).
export async function GET() {
  await connection();
  return Response.json({ status: "UP" });
}
